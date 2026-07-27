package com.ivanliu.ragproject.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagEvaluationAnswerClientTest {

    private final RagEvaluationAnswerClient client = new RagEvaluationAnswerClient(
            null,
            null,
            null,
            null,
            new ObjectMapper()
    );

    @Test
    void parsesJsonAnswerAndKeepsOnlyRetrievedCitations() {
        RagEvaluationAnswerClient.ParsedAnswer answer = client.parseAnswer(
                "```json\n{\"answer\":\"Ada Lovelace\",\"citedPassageIds\":[\"p-2\",\"p-2\"]}\n```",
                passages()
        );

        assertEquals("Ada Lovelace", answer.answer());
        assertEquals(List.of("p-2"), answer.citedPassageIds());
        assertFalse(answer.parseError());
    }

    @Test
    void marksHallucinatedCitationAsParseError() {
        RagEvaluationAnswerClient.ParsedAnswer answer = client.parseAnswer(
                "{\"answer\":\"Ada Lovelace\",\"citedPassageIds\":[\"not-retrieved\"]}",
                passages()
        );

        assertEquals(List.of(), answer.citedPassageIds());
        assertTrue(answer.parseError());
    }

    @Test
    void canonicalizesAbstentionAndRemovesCitations() {
        RagEvaluationAnswerClient.ParsedAnswer answer = client.parseAnswer(
                "{\"answer\":\"insufficient_evidence\",\"citedPassageIds\":[\"p-1\"]}",
                passages()
        );

        assertEquals("INSUFFICIENT_EVIDENCE", answer.answer());
        assertEquals(List.of(), answer.citedPassageIds());
        assertFalse(answer.parseError());
    }

    @Test
    void disablesDeepSeekThinkingAndRequestsJsonOutput() {
        Map<String, Object> request = RagEvaluationAnswerClient.buildRequest(
                "deepseek",
                "deepseek-v4-flash",
                List.of(Map.of("role", "user", "content", "Return JSON")),
                200
        );

        assertEquals(Map.of("type", "disabled"), request.get("thinking"));
        assertEquals(Map.of("type", "json_object"), request.get("response_format"));
        assertEquals(200, request.get("max_tokens"));
    }

    @Test
    void leavesProviderSpecificDeepSeekOptionsOffOtherProviders() {
        Map<String, Object> request = RagEvaluationAnswerClient.buildRequest(
                "qwen",
                "qwen-flash",
                List.of(Map.of("role", "user", "content", "Return JSON")),
                200
        );

        assertFalse(request.containsKey("thinking"));
        assertFalse(request.containsKey("response_format"));
    }

    private List<RagEvaluationExecutor.RetrievedPassage> passages() {
        return List.of(
                new RagEvaluationExecutor.RetrievedPassage("p-1", "One", "First passage", 2.0),
                new RagEvaluationExecutor.RetrievedPassage("p-2", "Two", "Second passage", 1.0)
        );
    }
}
