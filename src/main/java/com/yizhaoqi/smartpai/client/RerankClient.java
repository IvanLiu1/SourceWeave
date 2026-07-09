package com.yizhaoqi.smartpai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 重排客户端：调用 DashScope gte-rerank，对召回的候选文档按与 query 的相关性重新打分排序。
 * <p>
 * 设计原则：<b>绝不弄坏搜索</b>。未启用、缺少 key、或任何调用异常时都返回 {@code null}，
 * 由调用方回退到原始检索顺序。
 */
@Component
public class RerankClient {

    private static final Logger logger = LoggerFactory.getLogger(RerankClient.class);

    @Value("${rerank.enabled:true}")
    private boolean enabled;

    @Value("${rerank.api.url:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}")
    private String apiUrl;

    @Value("${rerank.api.key:}")
    private String apiKey;

    @Value("${rerank.api.model:gte-rerank-v2}")
    private String model;

    @Value("${rerank.timeout-seconds:10}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper;

    public RerankClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 是否可用：开关打开且配置了 key。
     */
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * 对候选文档重排。
     *
     * @param query     查询串
     * @param documents 候选文档文本，顺序需与调用方候选列表一致（返回的 index 指向此列表）
     * @param topN      返回前 N 个
     * @return 按相关性降序的 (index, score) 列表；未启用 / 入参非法 / 调用失败时返回 {@code null}
     */
    public List<RerankResult> rerank(String query, List<String> documents, int topN) {
        if (!isEnabled()) {
            return null;
        }
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("documents", documents);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("return_documents", false);
            parameters.put("top_n", Math.min(topN, documents.size()));

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", input);
            body.put("parameters", parameters);

            String response = buildClient().post()
                    .uri(apiUrl)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(timeoutSeconds));

            return parseResponse(response);
        } catch (WebClientResponseException e) {
            logger.warn("重排 API 调用失败，回退原始顺序 - HTTP {}: {}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            logger.warn("重排调用异常，回退原始顺序: {}", e.getMessage());
            return null;
        }
    }

    private WebClient buildClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                // 候选块文本可能较大，放宽内存缓冲上限到 16MB
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    private List<RerankResult> parseResponse(String response) throws Exception {
        JsonNode root = objectMapper.readTree(response);
        JsonNode results = root.path("output").path("results");
        if (!results.isArray() || results.isEmpty()) {
            logger.warn("重排响应缺少 output.results，回退原始顺序: {}", response);
            return null;
        }
        List<RerankResult> list = new ArrayList<>(results.size());
        for (JsonNode item : results) {
            int index = item.path("index").asInt(-1);
            double score = item.path("relevance_score").asDouble(0.0);
            if (index >= 0) {
                list.add(new RerankResult(index, score));
            }
        }
        return list.isEmpty() ? null : list;
    }

    /**
     * 重排结果：{@code index} 指向传入 documents 的下标，{@code score} 为相关性分。
     */
    public record RerankResult(int index, double score) {
    }
}
