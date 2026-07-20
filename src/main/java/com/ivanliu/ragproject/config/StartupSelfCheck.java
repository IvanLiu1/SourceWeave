package com.ivanliu.ragproject.config;

import com.ivanliu.ragproject.service.ModelProviderConfigService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动自检：确保 MinIO 桶存在（数据卷重建后自愈），并校验模型 Provider 的 key 配置，
 * 让"上传后才发现向量化 401"这类静默故障在启动日志里提前暴露。
 * 任何检查失败都只告警不阻断启动，保持后端可在基础设施未就绪时先起来。
 */
@Component
@Order(0)
public class StartupSelfCheck implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(StartupSelfCheck.class);

    private final MinioClient minioClient;
    private final ModelProviderConfigService modelProviderConfigService;

    @Value("${minio.bucketName:uploads}")
    private String bucketName;

    public StartupSelfCheck(MinioClient minioClient, ModelProviderConfigService modelProviderConfigService) {
        this.minioClient = minioClient;
        this.modelProviderConfigService = modelProviderConfigService;
    }

    @Override
    public void run(String... args) {
        ensureMinioBucket();
        checkProviderKey(ModelProviderConfigService.SCOPE_LLM, "聊天问答");
        checkProviderKey(ModelProviderConfigService.SCOPE_EMBEDDING, "文档向量化与检索重排");
    }

    private void ensureMinioBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (exists) {
                logger.info("MinIO 桶自检通过: {}", bucketName);
                return;
            }
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            logger.warn("MinIO 桶不存在，已自动创建: {}。若因数据卷重建导致，历史对象已丢失，数据库中的旧文件记录需重新上传或清理", bucketName);
        } catch (Exception e) {
            logger.error("MinIO 桶自检失败（不阻断启动，MinIO 恢复前文件上传/预览不可用）: {}", e.getMessage());
        }
    }

    private void checkProviderKey(String scope, String affectedFeature) {
        try {
            ModelProviderConfigService.ActiveProviderView provider = modelProviderConfigService.getActiveProvider(scope);
            if (provider.apiKey() == null || provider.apiKey().isBlank()) {
                logger.warn("模型配置自检: {} 作用域的活动 Provider [{}] 未配置 API key，{}将不可用（调用会返回 401）。"
                        + "请在 .env 或管理后台\"模型提供商\"页面配置", scope, provider.provider(), affectedFeature);
            } else {
                logger.info("模型配置自检通过: scope={}, provider={}, model={}", scope, provider.provider(), provider.model());
            }
        } catch (Exception e) {
            logger.warn("模型配置自检: {} 作用域无可用的活动 Provider（{}），{}将不可用", scope, e.getMessage(), affectedFeature);
        }
    }
}
