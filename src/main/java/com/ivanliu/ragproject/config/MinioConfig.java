package com.ivanliu.ragproject.config;

import io.minio.MinioClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Objects;

@Getter
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretKey;

    @Value("${minio.publicUrl}")
    private String publicUrl;

    /** 后端自用：走容器网络内部地址（如 http://minio:19000），用于上传、读流、删除等服务端操作 */
    @Primary
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 专用于生成预签名 URL：以浏览器可达的 publicUrl 为 endpoint。
     * <p>
     * 预签名走 AWS SigV4，Host 参与签名计算（URL 里的 X-Amz-SignedHeaders=host），
     * MinIO 收到请求时会用实际 Host 重算签名比对。因此必须用浏览器最终访问的地址来签名；
     * 若先用内网地址签名再把域名替换成公网域名，Host 与签名不匹配，必然 403 SignatureDoesNotMatch。
     * <p>
     * 两个地址相同时（本地开发常见）直接复用主客户端，避免多维护一套连接池。
     */
    @Bean
    public MinioClient presignedMinioClient(MinioClient minioClient) {
        if (Objects.equals(endpoint, publicUrl)) {
            return minioClient;
        }
        return MinioClient.builder()
                .endpoint(publicUrl)
                .credentials(accessKey, secretKey)
                .build();
    }
}
