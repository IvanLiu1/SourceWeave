package com.ivanliu.ragproject.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.ivanliu.ragproject.entity.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ivanliu.ragproject.config.AiProperties;
import com.ivanliu.ragproject.service.ModelProviderConfigService;
import com.ivanliu.ragproject.service.UsageQuotaService;
import reactor.core.Disposable;

@Service
public class DeepSeekClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final AiProperties aiProperties;
    private final UsageQuotaService usageQuotaService;
    private final ModelProviderConfigService modelProviderConfigService;
    private final ObjectMapper objectMapper;
    private final ProviderWebClientFactory webClientFactory;
    private static final Logger logger = LoggerFactory.getLogger(DeepSeekClient.class);
    
    public DeepSeekClient(@Value("${deepseek.api.url}") String apiUrl,
                         @Value("${deepseek.api.key}") String apiKey,
                         @Value("${deepseek.api.model}") String model,
                         AiProperties aiProperties,
                         UsageQuotaService usageQuotaService,
                         ModelProviderConfigService modelProviderConfigService,
                         ProviderWebClientFactory webClientFactory) {
        this.webClientFactory = webClientFactory;
        this.webClient = webClientFactory.getClient(apiUrl, apiKey != null ? apiKey.trim() : null);
        this.apiKey = apiKey;
        this.model = model;
        this.aiProperties = aiProperties;
        this.usageQuotaService = usageQuotaService;
        this.modelProviderConfigService = modelProviderConfigService;
        this.objectMapper = new ObjectMapper();
    }
    
    public String summarize(String requesterId,
                            String topic,
                            List<SearchResult> searchResults,
                            Consumer<String> onChunk) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("摘要主题不能为空");
        }
        if (searchResults == null || searchResults.isEmpty()) {
            String noResult = "未检索到与主题相关的知识库文档，无法生成基于知识库的摘要。";
            if (onChunk != null) {
                onChunk.accept(noResult);
            }
            return noResult;
        }

        List<Map<String, String>> messages = buildSummaryMessages(topic, searchResults);
        int estimatedPromptTokens = usageQuotaService.estimateChatTokens(messages);
        int maxCompletionTokens = aiProperties.getGeneration().getMaxTokens() != null
                ? aiProperties.getGeneration().getMaxTokens()
                : 1600;
        UsageQuotaService.TokenReservation reservation = usageQuotaService.reserveLlmTokens(
                requesterId, estimatedPromptTokens, maxCompletionTokens);
        StreamUsageTracker usageTracker = null;

        try {
            SummaryEndpoint summaryEndpoint = resolveSummaryEndpoint();
            Map<String, Object> request = new HashMap<>();
            request.put("model", summaryEndpoint.model());
            request.put("messages", messages);
            request.put("stream", true);
            request.put("stream_options", Map.of("include_usage", true));
            request.put("max_tokens", maxCompletionTokens);
            AiProperties.Generation gen = aiProperties.getGeneration();
            if (gen.getTemperature() != null) {
                request.put("temperature", Math.min(gen.getTemperature(), 0.3d));
            } else {
                request.put("temperature", 0.2d);
            }
            if (gen.getTopP() != null) {
                request.put("top_p", gen.getTopP());
            }

            usageTracker = new StreamUsageTracker(reservation, estimatedPromptTokens);
            String summary = executeSummaryStream(summaryEndpoint, request, usageTracker, onChunk);
            logger.info("generate_summary 内部摘要完成: provider={}, model={}, promptTokensEstimate={}, summaryChars={}",
                    summaryEndpoint.provider(), summaryEndpoint.model(), estimatedPromptTokens, summary.length());
            return summary;
        } catch (Exception e) {
            if (usageTracker == null || !usageTracker.settled) {
                usageQuotaService.abortReservation(reservation);
            }
            throw new RuntimeException("生成知识库摘要失败", e);
        }
    }

    private String executeSummaryStream(SummaryEndpoint summaryEndpoint,
                                        Map<String, Object> request,
                                        StreamUsageTracker usageTracker,
                                        Consumer<String> onChunk) {
        CompletableFuture<String> summaryFuture = new CompletableFuture<>();
        long startedAt = System.currentTimeMillis();
        AtomicBoolean firstChunkLogged = new AtomicBoolean(false);
        AtomicInteger chunkCount = new AtomicInteger(0);
        Consumer<String> chunkConsumer = chunk -> {
            if (chunk != null && !chunk.isEmpty()) {
                int currentChunkCount = chunkCount.incrementAndGet();
                if (firstChunkLogged.compareAndSet(false, true)) {
                    logger.info("generate_summary 内部摘要收到首个流式 chunk: provider={}, model={}, elapsedMs={}, chunkChars={}",
                            summaryEndpoint.provider(), summaryEndpoint.model(), System.currentTimeMillis() - startedAt, chunk.length());
                }
                logger.debug("generate_summary 内部摘要流式 chunk: provider={}, model={}, chunkIndex={}, chunkChars={}",
                        summaryEndpoint.provider(), summaryEndpoint.model(), currentChunkCount, chunk.length());
            }
            if (onChunk != null) {
                onChunk.accept(chunk);
            }
        };

        Disposable subscription = summaryEndpoint.webClient().post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .subscribe(
                        chunk -> processChunk(chunk, usageTracker, chunkConsumer),
                        error -> {
                            settleUsage(usageTracker);
                            summaryFuture.completeExceptionally(error);
                        },
                        () -> {
                            settleUsage(usageTracker);
                            logger.info("generate_summary 内部摘要流结束: provider={}, model={}, elapsedMs={}, chunks={}, summaryChars={}",
                                    summaryEndpoint.provider(),
                                    summaryEndpoint.model(),
                                    System.currentTimeMillis() - startedAt,
                                    chunkCount.get(),
                                    usageTracker.responseContent.length());
                            summaryFuture.complete(usageTracker.responseContent.toString().trim());
                        }
                );

        try {
            String summary = summaryFuture.get(90, TimeUnit.SECONDS);
            if (summary == null || summary.isBlank()) {
                throw new IllegalStateException("DeepSeek 摘要流未返回有效内容");
            }
            return summary;
        } catch (TimeoutException e) {
            subscription.dispose();
            settleUsage(usageTracker);
            throw new RuntimeException("DeepSeek 摘要流式响应超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            subscription.dispose();
            settleUsage(usageTracker);
            throw new RuntimeException("DeepSeek 摘要流式响应被中断", e);
        } catch (ExecutionException e) {
            subscription.dispose();
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("DeepSeek 摘要流式响应失败", cause);
        }
    }

    private SummaryEndpoint resolveSummaryEndpoint() {
        try {
            ModelProviderConfigService.ActiveProviderView provider =
                    modelProviderConfigService.getActiveProvider(ModelProviderConfigService.SCOPE_LLM);
            WebClient client = webClientFactory.getClient(
                    ModelProviderConfigService.normalizeOpenAiCompatibleBaseUrl(provider.apiBaseUrl()),
                    provider.apiKey());
            return new SummaryEndpoint(client, provider.model(), provider.provider());
        } catch (Exception exception) {
            logger.warn("无法读取活动 LLM Provider，generate_summary 内部摘要回退到 deepseek.* 配置: {}", exception.getMessage());
            return new SummaryEndpoint(webClient, model, "deepseek");
        }
    }
    
    private List<Map<String, String>> buildSummaryMessages(String topic, List<SearchResult> searchResults) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", "你是 generate_summary 工具内部使用的知识库摘要模型。"
                        + "只基于提供的知识库片段生成结构化摘要，不要发起工具调用，不要把自己当作外层 ReAct 循环。"
                        + "输出应包含：核心结论、关键依据、可执行建议或待确认问题。"
        ));
        messages.add(Map.of(
                "role", "user",
                "content", "主题：" + topic + "\n\n知识库片段：\n" + buildSummaryContext(searchResults)
        ));
        return messages;
    }

    private String buildSummaryContext(List<SearchResult> searchResults) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            context.append("[").append(i + 1).append("] ");
            if (result.getFileName() != null && !result.getFileName().isBlank()) {
                context.append("文件：").append(result.getFileName()).append("，");
            }
            context.append("fileMd5=").append(result.getFileMd5())
                    .append("，chunkId=").append(result.getChunkId());
            if (result.getPageNumber() != null) {
                context.append("，page=").append(result.getPageNumber());
            }
            context.append("\n")
                    .append(limitText(result.getMatchedChunkText() != null ? result.getMatchedChunkText() : result.getTextContent(), 1800))
                    .append("\n\n");
        }
        return context.toString();
    }

    private String limitText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private record SummaryEndpoint(WebClient webClient, String model, String provider) {
    }
    
    private void processChunk(String rawChunk, StreamUsageTracker usageTracker, Consumer<String> onChunk) {
        try {
            for (String chunk : extractPayloads(rawChunk)) {
                if ("[DONE]".equals(chunk)) {
                    logger.debug("对话结束");
                    continue;
                }

                JsonNode node = objectMapper.readTree(chunk);
                JsonNode usageNode = node.path("usage");
                if (usageNode.isObject()) {
                    usageTracker.promptTokens = usageNode.path("prompt_tokens").asInt(usageTracker.promptTokens);
                    usageTracker.completionTokens = usageNode.path("completion_tokens").asInt(usageTracker.completionTokens);
                }

                String content = node.path("choices")
                        .path(0)
                        .path("delta")
                        .path("content")
                        .asText("");

                if (!content.isEmpty()) {
                    usageTracker.responseContent.append(content);
                    onChunk.accept(content);
                }
            }
        } catch (Exception e) {
            logger.error("处理数据块时出错: {}", e.getMessage(), e);
        }
    }

    private List<String> extractPayloads(String rawChunk) {
        List<String> payloads = new ArrayList<>();
        if (rawChunk == null || rawChunk.isBlank()) {
            return payloads;
        }

        String trimmed = rawChunk.trim();
        for (String line : trimmed.split("\\r?\\n")) {
            String payload = line.trim();
            if (payload.isEmpty() || payload.startsWith(":")) {
                continue;
            }
            if (payload.startsWith("data:")) {
                payload = payload.substring(5).trim();
            }
            if (!payload.isEmpty()) {
                payloads.add(payload);
            }
        }

        if (payloads.isEmpty()) {
            payloads.add(trimmed);
        }
        return payloads;
    }

    private void settleUsage(StreamUsageTracker usageTracker) {
        if (usageTracker == null || usageTracker.settled) {
            return;
        }

        usageTracker.settled = true;
        int actualPromptTokens = usageTracker.promptTokens > 0
                ? usageTracker.promptTokens
                : usageTracker.estimatedPromptTokens;
        int actualCompletionTokens = usageTracker.completionTokens > 0
                ? usageTracker.completionTokens
                : usageQuotaService.estimateTextTokens(usageTracker.responseContent.toString());

        usageQuotaService.settleReservation(usageTracker.reservation, actualPromptTokens + actualCompletionTokens);
    }

    private static final class StreamUsageTracker {
        private final UsageQuotaService.TokenReservation reservation;
        private final int estimatedPromptTokens;
        private final StringBuilder responseContent = new StringBuilder();
        private volatile int promptTokens;
        private volatile int completionTokens;
        private volatile boolean settled;

        private StreamUsageTracker(UsageQuotaService.TokenReservation reservation, int estimatedPromptTokens) {
            this.reservation = reservation;
            this.estimatedPromptTokens = estimatedPromptTokens;
        }
    }
}
