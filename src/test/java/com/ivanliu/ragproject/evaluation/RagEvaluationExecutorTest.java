package com.ivanliu.ragproject.evaluation;

import com.ivanliu.ragproject.client.RerankClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagEvaluationExecutorTest {

    @Test
    void selectsRerankedTopFiveFromTheOriginalFifty() {
        List<RagEvaluationExecutor.RetrievedPassage> candidates = candidates(50);
        List<RerankClient.RerankResult> reranked = List.of(
                new RerankClient.RerankResult(49, 0.99),
                new RerankClient.RerankResult(40, 0.90),
                new RerankClient.RerankResult(30, 0.80),
                new RerankClient.RerankResult(20, 0.70),
                new RerankClient.RerankResult(10, 0.60)
        );

        List<RagEvaluationExecutor.RetrievedPassage> selected =
                RagEvaluationExecutor.selectReranked(candidates, reranked, 5);

        assertEquals(List.of("p-49", "p-40", "p-30", "p-20", "p-10"),
                selected.stream().map(RagEvaluationExecutor.RetrievedPassage::passageId).toList());
    }

    @Test
    void fallsBackToOriginalTopFiveWhenRerankFails() {
        List<RagEvaluationExecutor.RetrievedPassage> selected =
                RagEvaluationExecutor.selectReranked(candidates(50), null, 5);

        assertEquals(List.of("p-0", "p-1", "p-2", "p-3", "p-4"),
                selected.stream().map(RagEvaluationExecutor.RetrievedPassage::passageId).toList());
    }

    @Test
    void fallsBackWhenRerankResponseIsIncomplete() {
        List<RerankClient.RerankResult> partial = List.of(
                new RerankClient.RerankResult(9, 0.99),
                new RerankClient.RerankResult(9, 0.98),
                new RerankClient.RerankResult(99, 0.97)
        );

        List<RagEvaluationExecutor.RetrievedPassage> selected =
                RagEvaluationExecutor.selectReranked(candidates(50), partial, 5);

        assertEquals(List.of("p-0", "p-1", "p-2", "p-3", "p-4"),
                selected.stream().map(RagEvaluationExecutor.RetrievedPassage::passageId).toList());
    }

    @Test
    void onlyAllowsDestructiveOperationsOnIsolatedEvaluationIndices() {
        assertDoesNotThrow(() -> RagEvaluationExecutor.validateSafeIndexName("sourceweave_eval_rag_en_v1"));
        assertThrows(IllegalArgumentException.class,
                () -> RagEvaluationExecutor.validateSafeIndexName("knowledge_base"));
        assertThrows(IllegalArgumentException.class,
                () -> RagEvaluationExecutor.validateSafeIndexName("sourceweave_eval_*"));
    }

    private List<RagEvaluationExecutor.RetrievedPassage> candidates(int count) {
        List<RagEvaluationExecutor.RetrievedPassage> candidates = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            candidates.add(new RagEvaluationExecutor.RetrievedPassage(
                    "p-" + index,
                    "Title " + index,
                    "Text " + index,
                    100.0 - index
            ));
        }
        return candidates;
    }
}
