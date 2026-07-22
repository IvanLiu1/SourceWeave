package com.ivanliu.ragproject.utils;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SecretCryptoService {

    private static final Logger logger = LoggerFactory.getLogger(SecretCryptoService.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int WEAK_KEY_THRESHOLD_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${model-provider.security.secret-key:${jwt.secret-key:}}")
    private String base64Secret;

    /** 当前密钥：原始字节经 SHA-256 派生，恒为 32 字节 */
    private SecretKeySpec keySpec;

    /**
     * 历史密钥：把原始字节直接当 AES 密钥（派生方案之前的旧行为）。
     * 只有原始字节长度恰为 16/24/32 时旧方案才能启动，也才可能留下历史密文，
     * 因此其余长度下为 null。仅用于兼容解密与启动迁移。
     */
    private SecretKeySpec legacyKeySpec;

    @PostConstruct
    public void init() {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException("model-provider.security.secret-key 或 jwt.secret-key 必须配置");
        }

        byte[] raw = decodeRaw(base64Secret);

        // 用 SHA-256 把任意长度的原始字节派生为恒定 32 字节 AES 密钥。
        // 该密钥会兜底到 jwt.secret-key，而两者的长度约束并不一致：
        // JWT 签名(HMAC-SHA256)要求 >= 32 字节且不限上界，AES 要求精确 16/24/32 字节。
        // 直接复用会让一个完全合法的 JWT 密钥（如 48 字节）把启动打挂，派生后任意长度都可用。
        this.keySpec = new SecretKeySpec(sha256(raw), "AES");
        this.legacyKeySpec = isValidAesKeyLength(raw.length) ? new SecretKeySpec(raw, "AES") : null;

        if (raw.length < WEAK_KEY_THRESHOLD_BYTES) {
            logger.warn("模型配置加密密钥原始长度仅 {} 字节，强度偏低，建议用 `openssl rand -base64 32` 重新生成", raw.length);
        }
    }

    public String encrypt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("模型配置密钥加密失败", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }

        Parsed parsed = parse(ciphertext);
        String plaintext = tryDecrypt(keySpec, parsed);
        if (plaintext != null) {
            return plaintext;
        }

        // 新密钥解不开时回退到历史密钥，保证密钥派生方式变更前写入的密文仍可读
        if (legacyKeySpec != null) {
            plaintext = tryDecrypt(legacyKeySpec, parsed);
            if (plaintext != null) {
                return plaintext;
            }
        }
        throw new IllegalStateException("模型配置密钥解密失败");
    }

    /**
     * 密文是否仍以历史密钥加密（新密钥解不开、旧密钥能解开），供启动迁移筛选待重加密的记录。
     */
    public boolean needsReEncryption(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank() || legacyKeySpec == null) {
            return false;
        }
        Parsed parsed;
        try {
            parsed = parse(ciphertext);
        } catch (RuntimeException malformed) {
            return false;
        }
        return tryDecrypt(keySpec, parsed) == null && tryDecrypt(legacyKeySpec, parsed) != null;
    }

    public String mask(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (raw.length() <= 8) {
            return "****";
        }
        return raw.substring(0, 4) + "****" + raw.substring(raw.length() - 4);
    }

    /**
     * 解析密钥配置：优先按 Base64，不是合法 Base64 时退回 UTF-8 原始字节，
     * 避免非 Base64 的密钥值直接打挂启动。
     */
    private byte[] decodeRaw(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", impossible);
        }
    }

    private boolean isValidAesKeyLength(int length) {
        return length == 16 || length == 24 || length == 32;
    }

    private Parsed parse(String ciphertext) {
        String[] parts = ciphertext.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("密文格式不正确");
        }
        return new Parsed(Base64.getDecoder().decode(parts[0]), Base64.getDecoder().decode(parts[1]));
    }

    /** 解密成功返回明文，认证标签校验失败等任何异常一律返回 null，交由调用方决定是否换密钥重试 */
    private String tryDecrypt(SecretKeySpec key, Parsed parsed) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, parsed.iv()));
            return new String(cipher.doFinal(parsed.encrypted()), StandardCharsets.UTF_8);
        } catch (Exception failed) {
            return null;
        }
    }

    private record Parsed(byte[] iv, byte[] encrypted) {
    }
}
