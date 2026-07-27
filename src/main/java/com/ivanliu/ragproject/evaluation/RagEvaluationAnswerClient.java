package com.ivanliu.ragproject.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanliu.ragproject.client.ProviderWebClientFactory;
import com.ivanliu.ragproject.service.ModelProviderConfigService;
import com.ivanliu.ragproject.service.RateLimitService;
import com.ivanliu.ragproject.service.UsageQuotaService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RagEvaluationAnswerClient {

    static final String PROMPT_VERSION = "rag-eval-answer-v2";
    static final String ABSTENTION = "INSUFFICIENT_EVIDENCE";

    private final ModelProviderConfigService modelProviderConfigService;
    private final ProviderWebClientFactory webClientFactory;
    private final RateLimitService rateLimitService;
    private final UsageQuotaService usageQuotaService;
    private final ObjectMapper objectMapper;

    public RagEvaluationAnswerClient(ModelProviderConfigService modelProviderConfigService,
                                     ProviderWebClientFactory webClientFactory,
                                     RateLimitService rateLimitService,
                                     UsageQuotaService usageQuotaService,
                                     ObjectMapper objectMapper) {
        this.modelProviderConfigService = modelProviderConfigService;
        this.webClientFactory = webClientFactory;
        this.rateLimitService = rateLimitService;
        this.usageQuotaService = usageQuotaService;
        this.objectMapper = objectMapper;
    }

    public AnswerResult answer(String requesterId,
                               String question,
                               List<RagEvaluationExecutor.RetrievedPassage> passages,
                               int maxTokens,
                               int timeoutSeconds) {
        ModelProviderConfigService.ActiveProviderView provider =
                modelProviderConfigService.getActiveProvider(ModelProviderConfigService.SCOPE_LLM);
        List<Map<String, String>> messages = buildMessages(question, passages);
        int estimatedPromptTokens = usageQuotaService.estimateChatTokens(messages);
        UsageQuotaService.TokenReservationBundle reservation = rateLimitService.reserveLlmUsage(
                requesterId,
                estimatedPromptTokens,
                Math.max(maxTokens, 1)
        );

        try {
            Map<String, Object> request = buildRequest(
                    provider.provider(),
                    provider.model(),
                    messages,
                    maxTokens
            );

            WebClient client = webClientFactory.getClient(
                    ModelProviderConfigService.normalizeOpenAiCompatibleBaseUrl(provider.apiBaseUrl()),
                    provider.apiKey()
            );
            String rawResponse = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(Math.max(timeoutSeconds, 1)));

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choice = root.path("choices").path(0);
            String content = choice.path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                String finishReason = choice.path("finish_reason").asText("unknown");
                int reasoningTokens = root.path("usage")
                        .path("completion_tokens_details")
                        .path("reasoning_tokens")
                        .asInt(0);
                throw new IllegalStateException("Evaluation LLM returned an empty answer"
                        + " (finishReason=" + finishReason + ", reasoningTokens=" + reasoningTokens + ")");
            }
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(estimatedPromptTokens);
            int completionTokens = root.path("usage").path("completion_tokens")
                    .asInt(usageQuotaService.estimateTextTokens(content));
            usageQuotaService.settleReservation(reservation, promptTokens + completionTokens);

            ParsedAnswer parsed = parseAnswer(content, passages);
            return new AnswerResult(
                    parsed.answer(),
                    parsed.citedPassageIds(),
                    parsed.parseError(),
                    promptTokens,
                    completionTokens,
                    provider.provider() + ":" + provider.model()
            );
        } catch (Exception exception) {
            usageQuotaService.abortReservation(reservation);
            throw new IllegalStateException("Evaluation answer generation failed", exception);
        }
    }

    static Map<String, Object> buildRequest(String providerCode,
                                            String model,
                                            List<Map<String, String>> messages,
                                            int maxTokens) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("stream", false);
        request.put("temperature", 0);
        request.put("top_p", 1);
        request.put("max_tokens", Math.max(maxTokens, 1));
        if ("deepseek".equalsIgnoreCase(providerCode)) {
            // Evaluation needs a short, directly parseable answer. DeepSeek enables thinking by
            // default, and reasoning tokens can otherwise consume the entire output allowance.
            request.put("thinking", Map.of("type", "disabled"));
            request.put("response_format", Map.of("type", "json_object"));
        }
        return request;
    }

    List<Map<String, String>> buildMessages(String question,
                                            List<RagEvaluationExecutor.RetrievedPassage> passages) {
        String systemPrompt = "You are a deterministic RAG evaluation assistant. "
                + "Answer only from the supplied passages. If the passages do not support an answer, "
                + "set answer to exactly INSUFFICIENT_EVIDENCE. Return one JSON object only with this schema: "
                + "{\"answer\":\"short answer\",\"citedPassageIds\":[\"passage-id\"]}. "
                + "Citations must be passage IDs from the supplied passages and must directly support the answer. "
                + "Do not add markdown or explanation.";

        StringBuilder userPrompt = new StringBuilder("Question: ")
                .append(question)
                .append("\n\nPassages:\n");
        for (RagEvaluationExecutor.RetrievedPassage passage : passages) {
            userPrompt.append("[")
                    .append(passage.passageId())
                    .append("] ")
                    .append(passage.title())
                    .append("\n")
                    .append(passage.text())
                    .append("\n\n");
        }
        return List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt.toString().trim())
        );
    }

    ParsedAnswer parseAnswer(String content,
                             List<RagEvaluationExecutor.RetrievedPassage> passages) {
        Set<String> allowedIds = new LinkedHashSet<>();
        for (RagEvaluationExecutor.RetrievedPassage passage : passages) {
            allowedIds.add(passage.passageId());
        }

        try {
            String json = extractJsonObject(content);
            JsonNode root = objectMapper.readTree(json);
            String answer = root.path("answer").asText("").trim();
            if (answer.isBlank()) {
                throw new IllegalArgumentException("answer is empty");
            }
            List<String> citations = new ArrayList<>();
            boolean invalidCitation = false;
            JsonNode citedIds = root.path("citedPassageIds");
            if (citedIds.isArray()) {
                for (JsonNode citedId : citedIds) {
                    String value = citedId.asText("").trim();
                    if (allowedIds.contains(value) && !citations.contains(value)) {
                        citations.add(value);
                    } else if (!value.isBlank() && !allowedIds.contains(value)) {
                        invalidCitation = true;
                    }
                }
            } else {
                invalidCitation = true;
            }
            if (ABSTENTION.equalsIgnoreCase(answer)) {
                citations.clear();
                answer = ABSTENTION;
            }
            return new ParsedAnswer(answer, List.copyOf(citations), invalidCitation);
        } catch (Exception ignored) {
            return new ParsedAnswer(content.trim(), List.of(), true);
        }
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("No JSON object in evaluation answer");
        }
        return content.substring(start, end + 1);
    }

    public String currentModelVersion() {
        ModelProviderConfigService.ActiveProviderView provider =
                modelProviderConfigService.getActiveProvider(ModelProviderConfigService.SCOPE_LLM);
        return provider.provider() + ":" + provider.model();
    }

    record ParsedAnswer(String answer, List<String> citedPassageIds, boolean parseError) {
    }

    public record AnswerResult(String answer,
                               List<String> citedPassageIds,
                               boolean parseError,
                               int promptTokens,
                               int completionTokens,
                               String modelVersion) {
    }
}
