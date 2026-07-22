package com.ivanliu.ragproject.utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecretCryptoServiceTest {

    /** 32 字节，旧实现下唯一同时满足 JWT(>=32) 与 AES(16/24/32) 的长度 */
    private static final String KEY_32_BYTES =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    /** 测试 application.yml 里的 jwt.secret-key，解码为 41 字节：JWT 合法但 AES 非法 */
    private static final String KEY_41_BYTES = "bXlfc2VjdXJlX2tleV8xMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc=";

    public static SecretCryptoService serviceWith(String secret) {
        SecretCryptoService service = new SecretCryptoService();
        ReflectionTestUtils.setField(service, "base64Secret", secret);
        service.init();
        return service;
    }

    @Test
    void initAcceptsKeyOfAnyLength() {
        // 这三种在派生前的实现里都会因「必须是 16/24/32 字节」直接抛异常打挂启动
        assertDoesNotThrow(() -> serviceWith(KEY_41_BYTES), "41 字节的合法 JWT 密钥不应打挂加密服务");
        assertDoesNotThrow(() -> serviceWith(Base64.getEncoder().encodeToString(new byte[48])),
                "openssl rand -base64 48 生成的更强 JWT 密钥不应打挂加密服务");
        assertDoesNotThrow(() -> serviceWith("change-me"),
                "非 Base64 的密钥应按 UTF-8 原始字节处理而不是抛异常");
    }

    @Test
    void initStillRejectsBlankKey() {
        assertThrows(IllegalStateException.class, () -> serviceWith(""));
        assertThrows(IllegalStateException.class, () -> serviceWith("   "));
    }

    @Test
    void encryptDecryptRoundTrip() {
        SecretCryptoService service = serviceWith(KEY_41_BYTES);
        String plaintext = "sk-abcdef1234567890";

        String ciphertext = service.encrypt(plaintext);

        assertNotEquals(plaintext, ciphertext);
        assertTrue(ciphertext.contains(":"), "密文应为 base64(iv):base64(ct) 格式");
        assertEquals(plaintext, service.decrypt(ciphertext));
    }

    @Test
    void decryptsLegacyCiphertextWrittenWithRawKey() throws Exception {
        String plaintext = "sk-legacy-0987654321";
        String legacyCiphertext = encryptWithRawKeyAsLegacyDidPreviously(KEY_32_BYTES, plaintext);

        SecretCryptoService service = serviceWith(KEY_32_BYTES);

        // 派生方式变更后，旧密文必须仍可读，否则数据库里已有的 Provider 密钥会全部失效
        assertEquals(plaintext, service.decrypt(legacyCiphertext));
        assertTrue(service.needsReEncryption(legacyCiphertext), "旧密文应被识别为待迁移");
    }

    @Test
    void freshCiphertextIsNotFlaggedForMigration() {
        SecretCryptoService service = serviceWith(KEY_32_BYTES);

        String ciphertext = service.encrypt("sk-fresh-key");

        assertFalse(service.needsReEncryption(ciphertext), "新密钥写出的密文不应被重复迁移");
    }

    @Test
    void noLegacyKeyWhenRawLengthWasNeverValidForAes() {
        // 41 字节在旧实现下根本无法启动，不可能留下历史密文，因此不存在待迁移密文
        SecretCryptoService service = serviceWith(KEY_41_BYTES);

        assertFalse(service.needsReEncryption(service.encrypt("sk-any")));
        assertFalse(service.needsReEncryption("bm90LWEtcmVhbC1pdg==:bm90LXJlYWwtY2lwaGVydGV4dA=="));
    }

    @Test
    void decryptRejectsMalformedCiphertext() {
        SecretCryptoService service = serviceWith(KEY_32_BYTES);

        assertThrows(RuntimeException.class, () -> service.decrypt("没有冒号的密文"));
    }

    /** 复刻派生方案之前的加密方式：Base64 解码后的原始字节直接作为 AES 密钥 */
    public static String encryptWithRawKeyAsLegacyDidPreviously(String base64Key, String plaintext) throws Exception {
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(encrypted);
    }
}
