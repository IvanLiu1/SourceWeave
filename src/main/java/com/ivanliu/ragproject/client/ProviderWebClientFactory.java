package com.ivanliu.ragproject.client;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 (baseUrl, apiKey) 复用 WebClient 实例，避免每次请求重建客户端、丢失连接池。
 * Provider 可在管理后台运行时切换，因此不能是单一实例；缓存条目数与配置过的 Provider 数同阶。
 */
@Component
public class ProviderWebClientFactory {

    private final Map<String, WebClient> cache = new ConcurrentHashMap<>();

    public WebClient getClient(String baseUrl, String apiKey) {
        String cacheKey = baseUrl + "|" + (apiKey == null ? "" : apiKey);
        return cache.computeIfAbsent(cacheKey, ignored -> {
            WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
            if (apiKey != null && !apiKey.isBlank()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            return builder.build();
        });
    }
}
