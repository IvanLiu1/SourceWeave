package com.ivanliu.ragproject.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 预签名 URL 必须用浏览器可达的 publicUrl 签名：SigV4 把 Host 计入签名，
 * 先用内网 endpoint 签名再替换域名会导致 403 SignatureDoesNotMatch。
 * <p>
 * 这里只覆盖客户端选择逻辑。实际签名断言需要可达的 MinIO
 * （SDK 生成预签名前会发一次 region 探测请求），放在 CI 里会引入外部依赖，
 * 因此签名有效性用本地 MinIO 手工验证，不进测试套件。
 */
class MinioConfigTest {

    private static final String INTERNAL = "http://minio:19000";
    private static final String PUBLIC = "http://files.example.com:19000";

    private static MinioConfig config(String endpoint, String publicUrl) {
        MinioConfig config = new MinioConfig();
        ReflectionTestUtils.setField(config, "endpoint", endpoint);
        ReflectionTestUtils.setField(config, "publicUrl", publicUrl);
        ReflectionTestUtils.setField(config, "accessKey", "admin");
        ReflectionTestUtils.setField(config, "secretKey", "secret-key-for-test");
        return config;
    }

    @Test
    void buildsSeparateClientWhenPublicAddressDiffers() {
        MinioConfig config = config(INTERNAL, PUBLIC);
        MinioClient main = config.minioClient();

        // 地址不同才是真实部署形态：后端走容器网络，浏览器走宿主机映射端口
        assertNotSame(main, config.presignedMinioClient(main));
    }

    @Test
    void reusesMainClientWhenBothAddressesAreIdentical() {
        MinioConfig config = config(INTERNAL, INTERNAL);
        MinioClient main = config.minioClient();

        // 本地开发常见：两个地址相同，复用同一客户端避免多维护一套连接池
        assertSame(main, config.presignedMinioClient(main));
    }
}
