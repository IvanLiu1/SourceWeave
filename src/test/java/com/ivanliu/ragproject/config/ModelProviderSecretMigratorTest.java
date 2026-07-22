package com.ivanliu.ragproject.config;

import com.ivanliu.ragproject.model.ModelProviderConfig;
import com.ivanliu.ragproject.repository.ModelProviderConfigRepository;
import com.ivanliu.ragproject.utils.SecretCryptoService;
import com.ivanliu.ragproject.utils.SecretCryptoServiceTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelProviderSecretMigratorTest {

    private static final String KEY_32_BYTES =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private static ModelProviderConfig config(String ciphertext) {
        ModelProviderConfig config = new ModelProviderConfig();
        config.setId(1L);
        config.setConfigScope("llm");
        config.setProviderCode("deepseek");
        config.setApiKeyCiphertext(ciphertext);
        return config;
    }

    @Test
    void reEncryptsLegacyCiphertextAndKeepsPlaintextIntact() throws Exception {
        String plaintext = "sk-legacy-provider-key";
        String legacyCiphertext = SecretCryptoServiceTest.encryptWithRawKeyAsLegacyDidPreviously(KEY_32_BYTES, plaintext);
        ModelProviderConfig stored = config(legacyCiphertext);

        SecretCryptoService crypto = SecretCryptoServiceTest.serviceWith(KEY_32_BYTES);
        ModelProviderConfigRepository repository = mock(ModelProviderConfigRepository.class);
        when(repository.findAll()).thenReturn(List.of(stored));

        new ModelProviderSecretMigrator(repository, crypto).run();

        verify(repository).save(stored);
        assertNotEquals(legacyCiphertext, stored.getApiKeyCiphertext(), "密文应已用新密钥重写");
        assertEquals(plaintext, crypto.decrypt(stored.getApiKeyCiphertext()), "迁移不得改变明文");
        assertFalse(crypto.needsReEncryption(stored.getApiKeyCiphertext()), "迁移后不应再被标记为待迁移");
    }

    @Test
    void leavesAlreadyMigratedRowsUntouched() {
        SecretCryptoService crypto = SecretCryptoServiceTest.serviceWith(KEY_32_BYTES);
        ModelProviderConfig stored = config(crypto.encrypt("sk-already-new"));

        ModelProviderConfigRepository repository = mock(ModelProviderConfigRepository.class);
        when(repository.findAll()).thenReturn(List.of(stored));

        new ModelProviderSecretMigrator(repository, crypto).run();

        verify(repository, never()).save(any());
    }

    @Test
    void skipsRowsWithoutCiphertext() {
        SecretCryptoService crypto = SecretCryptoServiceTest.serviceWith(KEY_32_BYTES);
        ModelProviderConfigRepository repository = mock(ModelProviderConfigRepository.class);
        when(repository.findAll()).thenReturn(List.of(config(null), config("")));

        new ModelProviderSecretMigrator(repository, crypto).run();

        verify(repository, never()).save(any());
    }

    @Test
    void repositoryFailureDoesNotBreakStartup() {
        SecretCryptoService crypto = SecretCryptoServiceTest.serviceWith(KEY_32_BYTES);
        ModelProviderConfigRepository repository = mock(ModelProviderConfigRepository.class);
        when(repository.findAll()).thenThrow(new RuntimeException("数据库不可用"));

        // 与 StartupSelfCheck 一致：基础设施未就绪时只告警，不应阻断启动
        new ModelProviderSecretMigrator(repository, crypto).run();
    }
}
