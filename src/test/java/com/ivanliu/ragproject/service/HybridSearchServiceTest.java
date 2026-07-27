package com.ivanliu.ragproject.service;

import com.ivanliu.ragproject.client.RerankClient;
import com.ivanliu.ragproject.entity.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridSearchServiceTest {

    @Mock
    private RerankClient rerankClient;

    private HybridSearchService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new HybridSearchService();
        ReflectionTestUtils.setField(service, "rerankClient", rerankClient);
        ReflectionTestUtils.setField(service, "rerankCandidateSize", 50);
    }

    @Test
    void candidateFetchSizeIsFixedIndependentlyOfRerankSwitch() {
        Integer firstRunSize = ReflectionTestUtils.invokeMethod(service, "resolveCandidateFetchSize", 5);
        Integer secondRunSize = ReflectionTestUtils.invokeMethod(service, "resolveCandidateFetchSize", 5);

        assertEquals(50, firstRunSize);
        assertEquals(50, secondRunSize);
        verify(rerankClient, never()).isEnabled();
    }

    @Test
    void baselineTakesOriginalTopFiveFromSameFiftyCandidates() {
        when(rerankClient.isEnabled()).thenReturn(false);
        List<SearchResult> candidates = candidates(50);

        List<SearchResult> selected = ReflectionTestUtils.invokeMethod(
                service, "applyRerank", "evaluation query", candidates, 5);

        assertEquals(List.of(0, 1, 2, 3, 4), selected.stream().map(SearchResult::getChunkId).toList());
        verify(rerankClient, never()).rerank(eq("evaluation query"), anyList(), eq(5));
    }

    @Test
    void rerankReceivesAllFiftyCandidatesBeforeTakingTopFive() {
        when(rerankClient.isEnabled()).thenReturn(true);
        when(rerankClient.rerank(eq("evaluation query"), anyList(), eq(5)))
                .thenReturn(List.of(
                        new RerankClient.RerankResult(49, 0.99),
                        new RerankClient.RerankResult(40, 0.90),
                        new RerankClient.RerankResult(30, 0.80),
                        new RerankClient.RerankResult(20, 0.70),
                        new RerankClient.RerankResult(10, 0.60)
                ));

        List<SearchResult> selected = ReflectionTestUtils.invokeMethod(
                service, "applyRerank", "evaluation query", candidates(50), 5);

        ArgumentCaptor<List<String>> documents = ArgumentCaptor.forClass(List.class);
        verify(rerankClient).rerank(eq("evaluation query"), documents.capture(), eq(5));
        assertEquals(50, documents.getValue().size());
        assertEquals(List.of(49, 40, 30, 20, 10), selected.stream().map(SearchResult::getChunkId).toList());
        assertEquals("HYBRID_RERANK", selected.get(0).getRetrievalMode());
        assertEquals(0.99, selected.get(0).getScore());
    }

    private List<SearchResult> candidates(int count) {
        List<SearchResult> results = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            results.add(new SearchResult("file-" + i, i, "candidate-" + i, 100d - i,
                    "user", "org", true, "file-" + i + ".txt", null, null,
                    "HYBRID", "candidate-" + i));
        }
        return results;
    }
}
