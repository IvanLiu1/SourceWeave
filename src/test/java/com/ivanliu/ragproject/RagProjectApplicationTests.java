package com.ivanliu.ragproject;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// properties 内联覆盖数据源:本机 .env 会被 DotenvEnvironmentPostProcessor 以高于测试 application.yml 的优先级注入,
// 不固定 H2 时上下文会拿到 .env 里的 MySQL URL
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ragproject;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class RagProjectApplicationTests {

    @Test
    void contextLoads() {
    }

}
