package com.ivanliu.ragproject.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivanliu.ragproject.client.ProviderWebClientFactory;
import com.ivanliu.ragproject.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class LlmProviderRouter {

    private static final Logger logger = LoggerFactory.getLogger(LlmProviderRouter.class);
    private static final int REACT_HISTORY_MAX_MESSAGES = 6;
    private static final int REACT_HISTORY_MAX_CONTENT_CHARS = 800;
    private static final int DEFAULT_REACT_MAX_COMPLETION_TOKENS = 2000;
    private static final String DEFAULT_LOCALE = "zh-CN";
    private static final String ENGLISH_LOCALE = "en-US";

    private final AiProperties aiProperties;
    private final RateLimitService rateLimitService;
    private final UsageQuotaService usageQuotaService;
    private final ModelProviderConfigService modelProviderConfigService;
    private final ObjectMapper objectMapper;
    private final ProviderWebClientFactory webClientFactory;

    public LlmProviderRouter(AiProperties aiProperties,
                             RateLimitService rateLimitService,
                             UsageQuotaService usageQuotaService,
                             ModelProviderConfigService modelProviderConfigService,
                             ObjectMapper objectMapper,
                             ProviderWebClientFactory webClientFactory) {
        this.aiProperties = aiProperties;
        this.rateLimitService = rateLimitService;
        this.usageQuotaService = usageQuotaService;
        this.modelProviderConfigService = modelProviderConfigService;
        this.objectMapper = objectMapper;
        this.webClientFactory = webClientFactory;
    }

    public List<Map<String, Object>> buildReActMessages(String userMessage,
                                                        String context,
                                                        List<Map<String, String>> history) {
        return buildReActMessages(userMessage, context, history, "", DEFAULT_LOCALE);
    }

    public List<Map<String, Object>> buildReActMessages(String userMessage,
                                                        String context,
                                                        List<Map<String, String>> history,
                                                        String feedbackGuidance) {
        return buildReActMessages(userMessage, context, history, feedbackGuidance, DEFAULT_LOCALE);
    }

    public List<Map<String, Object>> buildReActMessages(String userMessage,
                                                        String context,
                                                        List<Map<String, String>> history,
                                                        String feedbackGuidance,
                                                        String locale) {
        List<Map<String, Object>> messages = new ArrayList<>();
        AiProperties.Prompt promptCfg = aiProperties.getPrompt();

        StringBuilder sysBuilder = new StringBuilder();
        if (promptCfg.getRules() != null) {
            sysBuilder.append(promptCfg.getRules()).append("\n\n");
        }
        appendLocalizedResponseContract(sysBuilder, normalizeLocale(locale));
        sysBuilder.append("This is a knowledge-base-first assistant. Unless a request matches an explicit exemption below, every user question must call search_knowledge before answering.\n\n")
                .append("Mandatory retrieval policy:\n")
                .append("1. Call search_knowledge whenever a request mentions an entity, name, acronym, product, project, term, process, feature, implementation, background, comparison, citation, or contextual reference such as this/it/that/the above. Retrieve even when you believe you already know the answer.\n")
                .append("2. Preserve the user's core nouns, acronyms, qualifiers, and polarity when constructing the query. Do not replace them with generic keywords. A faithful paraphrase may be added to the same query.\n")
                .append("3. For requests to organize or summarize knowledge-base material, first call search_knowledge to identify the material, then call generate_summary.\n\n")
                .append("Retrieval exemptions (the request must clearly match one):\n")
                .append("- a greeting or social pleasantry;\n")
                .append("- a pure translation request that does not involve system-specific terminology;\n")
                .append("- a creative-writing request unrelated to the knowledge base;\n")
                .append("- general programming syntax, arithmetic, or common knowledge that needs no proprietary information;\n")
                .append("- the user explicitly says not to search the knowledge base.\n\n")
                .append("Evidence and abstention policy:\n")
                .append("- Retrieved snippets are candidate evidence, not proof. Never answer merely because search_knowledge returned snippets.\n")
                .append("- Treat the question's subject, object, relationship, quantity, qualifier, comparison, and polarity as immutable. Before answering, verify each one against the evidence. Never silently correct, reinterpret, or substitute the user's premise.\n")
                .append("- A changed entity, number, relationship, or polarity is a contradiction, not a close match. For example: if the question says 'did not join' but the evidence says 'joined', if it says 139 but the evidence says 129, or if it asks about entity A but the evidence describes entity B, refuse to answer.\n")
                .append("- Then verify that the evidence explicitly entails the complete answer to the exact question. Topic overlap, a nearby answer-like phrase, a repaired premise, an implication that reverses the statement, or plausible outside knowledge is not sufficient.\n")
                .append("- If any required part is unsupported or contradicted, state that the available knowledge-base evidence is insufficient. Do not guess and do not turn a related fact into an answer.\n")
                .append("- Source numbers [N] increase globally across retrieval rounds. Cite the actual numbers shown by the tool; never restart numbering at [1]. Evidence from different rounds may be combined.\n")
                .append("- A LOW_CONFIDENCE search result contains no usable evidence. Retry with a faithful query refinement at most twice. If confidence remains low, refuse to answer and ask for a clarifying detail.\n")
                .append("- If search returns no usable snippets, retry when a faithful refinement is possible; otherwise refuse to answer.\n")
                .append("- On tool failure, use the error to decide whether to retry or change the query. Never invent tool output.\n")
                .append("- Older tool results may be compressed into stubs. Their source numbers remain valid. Call fetch_chunk only when exact original wording is required.\n")
                .append("- Use the relevant tool call to record feedback or inspect knowledge-base statistics.\n")
                .append("After tool use, continue reasoning and provide the final response under the response contract above.\n\n");
        if (feedbackGuidance != null && !feedbackGuidance.isBlank()) {
            sysBuilder.append(feedbackGuidance.trim()).append("\n\n");
        }

        String refStart = promptCfg.getRefStart() != null ? promptCfg.getRefStart() : "<<REF>>";
        String refEnd = promptCfg.getRefEnd() != null ? promptCfg.getRefEnd() : "<<END>>";
        sysBuilder.append(refStart).append("\n");
        if (context != null && !context.isEmpty()) {
            sysBuilder.append(context);
        } else {
            sysBuilder.append(promptCfg.getNoResultText() != null ? promptCfg.getNoResultText() : "（本轮无预置检索结果，可按需调用工具）").append("\n");
        }
        sysBuilder.append(refEnd);

        messages.add(newMessage("system", sysBuilder.toString()));
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - REACT_HISTORY_MAX_MESSAGES);
            for (Map<String, String> message : history.subList(start, history.size())) {
                String role = message.get("role");
                String content = message.get("content");
                if (role == null || role.isBlank() || content == null || content.isBlank()) {
                    continue;
                }
                if ("user".equals(role) || "assistant".equals(role) || "system".equals(role)) {
                    messages.add(newMessage(role, limitText(content, REACT_HISTORY_MAX_CONTENT_CHARS)));
                }
            }
        }
        messages.add(newMessage("user", userMessage));
        return messages;
    }

    private String normalizeLocale(String locale) {
        return ENGLISH_LOCALE.equals(locale) ? ENGLISH_LOCALE : DEFAULT_LOCALE;
    }

    private void appendLocalizedResponseContract(StringBuilder builder, String locale) {
        if (ENGLISH_LOCALE.equals(locale)) {
            builder.append("Response requirements for this turn:\n")
                    .append("1. Answer only in English.\n")
                    .append("2. State the conclusion first, followed by supporting evidence.\n")
                    .append("3. Cite references at sentence ends as (Source #N: filename), or (Source #N: filename | Page X) when a page number is available.\n")
                    .append("4. If the available information is insufficient, say that no relevant information is available and explain why.\n\n");
            return;
        }

        builder.append("Response requirements for this turn:\n")
                .append("1. Answer only in Simplified Chinese.\n")
                .append("2. State the conclusion first, followed by supporting evidence.\n")
                .append("3. Cite references at sentence ends as （来源#N: 文件名）, or （来源#N: 文件名 | 第X页） when a page number is available.\n")
                .append("4. If the available evidence is insufficient, say so in Simplified Chinese and identify what support is missing.\n\n");
    }

    public StreamHandle streamReActTurn(String requesterId,
                                        List<Map<String, Object>> messages,
                                        List<AgentToolRegistry.AgentTool> tools,
                                        int maxCompletionTokens,
                                        Consumer<String> onChunk,
                                        Consumer<Throwable> onError,
                                        Consumer<ReActTurn> onComplete) {
        ModelProviderConfigService.ActiveProviderView provider =
                modelProviderConfigService.getActiveProvider(ModelProviderConfigService.SCOPE_LLM);
        Map<String, Object> request = buildReActRequest(provider.model(), messages, tools, maxCompletionTokens, true);
        int estimatedPromptTokens = estimateObjectMessagesTokens(messages)
                + (tools == null || tools.isEmpty() ? 0 : estimateToolsTokens(tools));
        UsageQuotaService.TokenReservationBundle reservation = rateLimitService.reserveLlmUsage(
                requesterId, estimatedPromptTokens, Math.max(maxCompletionTokens, 1));
        ReActStreamAccumulator accumulator = new ReActStreamAccumulator(reservation, estimatedPromptTokens);

        try {
            Disposable subscription = buildClient(provider)
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .subscribe(
                            chunk -> processReActStreamChunk(chunk, accumulator, onChunk),
                            error -> {
                                logProviderError("ReAct 流式回合调用失败", error);
                                settleReActStreamUsage(accumulator);
                                onError.accept(error);
                            },
                            () -> {
                                settleReActStreamUsage(accumulator);
                                ReActTurn turn = accumulator.toTurn();
                                logger.info("ReAct 流式回合完成: provider={}, model={}, finishReason={}, toolCalls={}, contentChars={}",
                                        provider.provider(),
                                        provider.model(),
                                        turn.finishReason(),
                                        turn.toolCalls().size(),
                                        turn.content().length());
                                onComplete.accept(turn);
                            }
                    );
            return new StreamHandle(subscription, () -> settleReActStreamUsage(accumulator));
        } catch (Exception exception) {
            usageQuotaService.abortReservation(reservation);
            throw exception;
        }
    }

    private WebClient buildClient(ModelProviderConfigService.ActiveProviderView provider) {
        return webClientFactory.getClient(
                ModelProviderConfigService.normalizeOpenAiCompatibleBaseUrl(provider.apiBaseUrl()),
                provider.apiKey());
    }

    private void logProviderError(String message, Throwable error) {
        if (error instanceof WebClientResponseException responseException) {
            logger.warn("{}: status={}, body={}",
                    message,
                    responseException.getStatusCode(),
                    responseException.getResponseBodyAsString(),
                    responseException);
            return;
        }
        logger.warn("{}: {}", message, error.getMessage(), error);
    }

    private Map<String, Object> buildReActRequest(String model,
                                                  List<Map<String, Object>> messages,
                                                  List<AgentToolRegistry.AgentTool> tools,
                                                  int maxCompletionTokens,
                                                  boolean stream) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("stream", stream);
        request.put("max_tokens", Math.max(maxCompletionTokens, 1));
        if (stream) {
            request.put("stream_options", Map.of("include_usage", true));
        }

        AiProperties.Generation gen = aiProperties.getGeneration();
        if (gen.getTemperature() != null) {
            request.put("temperature", gen.getTemperature());
        }
        if (gen.getTopP() != null) {
            request.put("top_p", gen.getTopP());
        }
        if (tools != null && !tools.isEmpty()) {
            request.put("tools", buildOpenAiTools(tools));
            request.put("tool_choice", "auto");
        }
        return request;
    }

    private Map<String, Object> newMessage(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private String limitText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(maxChars, 0)) + "...";
    }

    private List<Map<String, Object>> buildOpenAiTools(List<AgentToolRegistry.AgentTool> tools) {
        List<Map<String, Object>> openAiTools = new ArrayList<>();
        for (AgentToolRegistry.AgentTool tool : tools) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.parameters());

            Map<String, Object> toolSchema = new LinkedHashMap<>();
            toolSchema.put("type", "function");
            toolSchema.put("function", function);
            openAiTools.add(toolSchema);
        }
        return openAiTools;
    }

    private int estimateToolsTokens(List<AgentToolRegistry.AgentTool> tools) {
        int tokens = 0;
        for (AgentToolRegistry.AgentTool tool : tools) {
            tokens += usageQuotaService.estimateTextTokens(tool.name());
            tokens += usageQuotaService.estimateTextTokens(tool.description());
            try {
                tokens += usageQuotaService.estimateTextTokens(objectMapper.writeValueAsString(tool.parameters()));
            } catch (Exception ignored) {
                tokens += 80;
            }
        }
        return tokens;
    }

    public int estimateObjectMessagesTokens(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int tokens = 0;
        for (Map<String, Object> message : messages) {
            tokens += 8;
            tokens += usageQuotaService.estimateTextTokens(String.valueOf(message.getOrDefault("role", "")));
            tokens += usageQuotaService.estimateTextTokens(String.valueOf(message.getOrDefault("content", "")));
            Object reasoningContent = message.get("reasoning_content");
            if (reasoningContent != null) {
                tokens += usageQuotaService.estimateTextTokens(String.valueOf(reasoningContent));
            }
            Object toolCalls = message.get("tool_calls");
            if (toolCalls != null) {
                try {
                    tokens += usageQuotaService.estimateTextTokens(objectMapper.writeValueAsString(toolCalls));
                } catch (Exception ignored) {
                    tokens += 128;
                }
            }
            Object toolCallId = message.get("tool_call_id");
            if (toolCallId != null) {
                tokens += usageQuotaService.estimateTextTokens(String.valueOf(toolCallId));
            }
        }
        return Math.max(tokens, 1);
    }

    private void processReActStreamChunk(String rawChunk,
                                         ReActStreamAccumulator accumulator,
                                         Consumer<String> onChunk) {
        try {
            for (String chunk : extractPayloads(rawChunk)) {
                if ("[DONE]".equals(chunk)) {
                    continue;
                }

                JsonNode node = objectMapper.readTree(chunk);
                JsonNode usageNode = node.path("usage");
                if (usageNode.isObject()) {
                    accumulator.promptTokens = usageNode.path("prompt_tokens").asInt(accumulator.promptTokens);
                    accumulator.completionTokens = usageNode.path("completion_tokens").asInt(accumulator.completionTokens);
                }

                JsonNode choiceNode = node.path("choices").path(0);
                if (!choiceNode.isObject()) {
                    continue;
                }

                JsonNode finishReasonNode = choiceNode.path("finish_reason");
                if (!finishReasonNode.isMissingNode() && !finishReasonNode.isNull()) {
                    String finishReason = finishReasonNode.asText("");
                    if (!finishReason.isBlank()) {
                        accumulator.finishReason = finishReason;
                    }
                }

                JsonNode delta = choiceNode.path("delta");
                String reasoningContent = delta.path("reasoning_content").asText("");
                if (!reasoningContent.isEmpty()) {
                    accumulator.reasoningContent.append(reasoningContent);
                }

                String content = delta.path("content").asText("");
                if (!content.isEmpty()) {
                    accumulator.content.append(content);
                    onChunk.accept(content);
                }

                JsonNode toolCallsNode = delta.path("tool_calls");
                if (toolCallsNode.isArray()) {
                    for (JsonNode toolCallDelta : toolCallsNode) {
                        accumulator.appendToolCallDelta(toolCallDelta);
                    }
                }
            }
        } catch (Exception exception) {
            logger.error("处理 ReAct 流式响应数据块失败: {}", exception.getMessage(), exception);
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

    private void settleReActStreamUsage(ReActStreamAccumulator accumulator) {
        if (accumulator == null || accumulator.settled) {
            return;
        }

        accumulator.settled = true;
        int actualPromptTokens = accumulator.promptTokens > 0
                ? accumulator.promptTokens
                : accumulator.estimatedPromptTokens;
        int actualCompletionTokens = accumulator.completionTokens > 0
                ? accumulator.completionTokens
                : usageQuotaService.estimateTextTokens(accumulator.content.toString())
                + estimateObjectMessagesTokens(List.of(accumulator.assistantMessage()));
        usageQuotaService.settleReservation(accumulator.reservation, actualPromptTokens + actualCompletionTokens);
    }

    private static final class ReActStreamAccumulator {
        private final UsageQuotaService.TokenReservationBundle reservation;
        private final int estimatedPromptTokens;
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoningContent = new StringBuilder();
        private final Map<Integer, StreamingToolCall> toolCalls = new LinkedHashMap<>();
        private volatile int promptTokens;
        private volatile int completionTokens;
        private volatile String finishReason;
        private volatile boolean settled;

        private ReActStreamAccumulator(UsageQuotaService.TokenReservationBundle reservation, int estimatedPromptTokens) {
            this.reservation = reservation;
            this.estimatedPromptTokens = estimatedPromptTokens;
        }

        private void appendToolCallDelta(JsonNode delta) {
            int index = delta.path("index").asInt(toolCalls.size());
            StreamingToolCall toolCall = toolCalls.computeIfAbsent(index, ignored -> new StreamingToolCall());
            String id = delta.path("id").asText("");
            if (!id.isBlank()) {
                toolCall.id = id;
            }
            String type = delta.path("type").asText("");
            if (!type.isBlank()) {
                toolCall.type = type;
            }
            JsonNode function = delta.path("function");
            if (function.isObject()) {
                String name = function.path("name").asText("");
                if (!name.isBlank()) {
                    toolCall.name.append(name);
                }
                String arguments = function.path("arguments").asText("");
                if (!arguments.isEmpty()) {
                    toolCall.arguments.append(arguments);
                }
            }
        }

        private Map<String, Object> assistantMessage() {
            Map<String, Object> message = new LinkedHashMap<>();
            List<Map<String, Object>> serializedToolCalls = serializedToolCalls();
            message.put("role", "assistant");
            if (!serializedToolCalls.isEmpty()) {
                String assistantContent = content.toString();
                message.put("content", assistantContent.isBlank() ? null : assistantContent);
                message.put("tool_calls", serializedToolCalls);
            } else {
                message.put("content", content.toString());
            }
            if (!reasoningContent.isEmpty()) {
                message.put("reasoning_content", reasoningContent.toString());
            }
            return message;
        }

        private List<Map<String, Object>> serializedToolCalls() {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (Map.Entry<Integer, StreamingToolCall> entry : toolCalls.entrySet()) {
                StreamingToolCall toolCall = entry.getValue();
                if (toolCall.name.isEmpty()) {
                    continue;
                }
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", toolCall.name.toString());
                function.put("arguments", toolCall.arguments.isEmpty() ? "{}" : toolCall.arguments.toString());

                Map<String, Object> call = new LinkedHashMap<>();
                call.put("id", toolCall.id == null || toolCall.id.isBlank() ? "call_" + entry.getKey() : toolCall.id);
                call.put("type", toolCall.type == null || toolCall.type.isBlank() ? "function" : toolCall.type);
                call.put("function", function);
                serialized.add(call);
            }
            return serialized;
        }

        private ReActTurn toTurn() {
            Map<String, Object> assistantMessage = assistantMessage();
            List<ToolCallDecision> decisions = new ArrayList<>();
            for (Map<String, Object> item : serializedToolCalls()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> function = (Map<String, Object>) item.get("function");
                String argumentsJson = String.valueOf(function.getOrDefault("arguments", "{}"));
                Map<String, Object> arguments;
                try {
                    arguments = new ObjectMapper().readValue(
                            argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson,
                            new TypeReference<Map<String, Object>>() {
                            });
                } catch (Exception ignored) {
                    arguments = Map.of();
                }
                decisions.add(new ToolCallDecision(
                        String.valueOf(item.getOrDefault("id", "")),
                        String.valueOf(function.getOrDefault("name", "")),
                        arguments
                ));
            }
            return new ReActTurn(
                    content.toString().trim(),
                    decisions,
                    assistantMessage,
                    finishReason == null || finishReason.isBlank() ? "unknown" : finishReason,
                    promptTokens > 0 ? promptTokens : estimatedPromptTokens,
                    completionTokens > 0 ? completionTokens : DEFAULT_REACT_MAX_COMPLETION_TOKENS
            );
        }
    }

    private static final class StreamingToolCall {
        private String id;
        private String type;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }

    public record StreamCompletion(
            String finishReason,
            int promptTokens,
            int completionTokens,
            int responseChars
    ) {
    }

    public record ToolCallDecision(
            String id,
            String name,
            Map<String, Object> arguments
    ) {
    }

    public record ReActTurn(
            String content,
            List<ToolCallDecision> toolCalls,
            Map<String, Object> assistantMessage,
            String finishReason,
            int promptTokens,
            int completionTokens
    ) {
    }

    public static final class StreamHandle {
        private final Disposable subscription;
        private final Runnable onCancel;

        private StreamHandle(Disposable subscription, Runnable onCancel) {
            this.subscription = subscription;
            this.onCancel = onCancel;
        }

        public void cancel() {
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }
}
