package com.ivanliu.ragproject.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ResourceLock("user.dir")
class DotenvEnvironmentPostProcessorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesEmptyApplicationDefaultsFromDotenv() throws Exception {
        Files.writeString(temporaryDirectory.resolve(".env"), """
                ADMIN_BOOTSTRAP_PASSWORD=dotenv-admin-password
                ELASTICSEARCH_PASSWORD=dotenv-elasticsearch-password
                DEEPSEEK_API_KEY=dotenv-deepseek-key
                EMBEDDING_API_KEY=dotenv-embedding-key
                """);

        String originalUserDir = System.getProperty("user.dir");
        Path applicationYaml = Path.of(originalUserDir, "src", "main", "resources", "application.yml");
        System.setProperty("user.dir", temporaryDirectory.toString());
        try {
            SpringApplication application = new SpringApplication(TestApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setLogStartupInfo(false);

            try (ConfigurableApplicationContext context = application.run(
                    "--spring.config.location=" + applicationYaml.toUri(),
                    "--spring.main.banner-mode=off",
                    "--spring.main.log-startup-info=false"
            )) {
                assertEquals("dotenv-admin-password",
                        context.getEnvironment().getProperty("admin.bootstrap.password"));
                assertEquals("dotenv-elasticsearch-password",
                        context.getEnvironment().getProperty("elasticsearch.password"));
                assertEquals("dotenv-deepseek-key",
                        context.getEnvironment().getProperty("deepseek.api.key"));
                assertEquals("dotenv-embedding-key",
                        context.getEnvironment().getProperty("embedding.api.key"));
                assertEquals("dotenv-embedding-key",
                        context.getEnvironment().getProperty("rerank.api.key"));
            }
        } finally {
            if (originalUserDir == null) {
                System.clearProperty("user.dir");
            } else {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    static class TestApplication {
    }
}
