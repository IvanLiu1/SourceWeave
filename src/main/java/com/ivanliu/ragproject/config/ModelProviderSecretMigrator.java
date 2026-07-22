package com.ivanliu.ragproject.config;

import com.ivanliu.ragproject.model.ModelProviderConfig;
import com.ivanliu.ragproject.repository.ModelProviderConfigRepository;
import com.ivanliu.ragproject.utils.SecretCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型 Provider 密钥迁移：AES 密钥的派生方式改为「原始字节 SHA-256」后，
 * 变更前写入的密文只能用历史密钥解开。启动时把这类密文解出来再用新密钥重新加密回写，
 * 让已有 Provider 配置无需人工重填。
 * 与 {@link StartupSelfCheck} 一致，失败只告警不阻断启动。
 */
@Component
@Order(-1)
public class ModelProviderSecretMigrator implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ModelProviderSecretMigrator.class);

    private final ModelProviderConfigRepository repository;
    private final SecretCryptoService secretCryptoService;

    public ModelProviderSecretMigrator(ModelProviderConfigRepository repository,
                                       SecretCryptoService secretCryptoService) {
        this.repository = repository;
        this.secretCryptoService = secretCryptoService;
    }

    @Override
    public void run(String... args) {
        try {
            List<ModelProviderConfig> configs = repository.findAll();
            int migrated = 0;
            int failed = 0;

            for (ModelProviderConfig config : configs) {
                if (!secretCryptoService.needsReEncryption(config.getApiKeyCiphertext())) {
                    continue;
                }
                try {
                    String plaintext = secretCryptoService.decrypt(config.getApiKeyCiphertext());
                    config.setApiKeyCiphertext(secretCryptoService.encrypt(plaintext));
                    repository.save(config);
                    migrated++;
                } catch (Exception exception) {
                    failed++;
                    logger.error("模型 Provider 密钥迁移失败（id={}, scope={}, provider={}），"
                                    + "需在管理后台「模型提供商」页面重新填写 API key: {}",
                            config.getId(), config.getConfigScope(), config.getProviderCode(), exception.getMessage());
                }
            }

            if (migrated > 0 || failed > 0) {
                logger.warn("模型 Provider 密钥迁移完成（加密密钥派生方式已改为 SHA-256）：成功 {} 条，失败 {} 条",
                        migrated, failed);
            } else {
                logger.info("模型 Provider 密钥自检通过：{} 条配置无需迁移", configs.size());
            }
        } catch (Exception exception) {
            logger.error("模型 Provider 密钥迁移检查失败（不阻断启动）: {}", exception.getMessage());
        }
    }
}
