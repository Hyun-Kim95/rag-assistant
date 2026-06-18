package com.example.ragassistant.service;

import com.example.ragassistant.config.RagProperties;
import com.example.ragassistant.domain.SearchHit;
import com.example.ragassistant.observability.QueryTelemetryContext;
import com.example.ragassistant.repository.ChunkRepository;
import com.example.ragassistant.repository.EmbeddingRepository;
import com.example.ragassistant.search.Reranker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Retriever 분기 검증 (DB·Ollama 없이 Mockito).
 * - rerank off: 기존 top-k 후보를 그대로 반환, Reranker 미호출
 * - rerank on : candidate-top-k로 넓게 뽑아 Reranker.rerank(topN) 호출
 * - 후보 없음 : Reranker 미호출, 빈 목록
 * hybrid-enabled=false(vector-only)로 고정해 분기만 본다.
 */
@ExtendWith(MockitoExtension.class)
class RetrieverTest {

    @Mock
    EmbeddingService embeddingService;
    @Mock
    EmbeddingRepository embeddingRepository;
    @Mock
    ChunkRepository chunkRepository;
    @Mock
    Reranker reranker;

    private final SearchHit hitA = new SearchHit(1L, "a.md", "ca", 0.8);
    private final SearchHit hitB = new SearchHit(2L, "b.md", "cb", 0.7);

    /**
     * rag 설정 헬퍼 — 필요한 값만 지정. hybrid off, min-score 0.
     */
    private RagProperties props(boolean rerankEnabled) {
        return new RagProperties(
                450, 150, /*topK*/10, /*minScore*/0.0, 768,
                /*hybridEnabled*/false, /*lexicalTopK*/10, /*lexicalMinScore*/0.1, /*rrfK*/60,
                /*candidateTopK*/30, /*rerankTopN*/6, rerankEnabled);
    }

    private Retriever retriever(RagProperties props) {
        return new Retriever(embeddingService, embeddingRepository, chunkRepository, props, reranker,
                new QueryTelemetryContext());
    }

    /**
     * rerank off: top-k(10) 후보를 그대로 반환, Reranker 미호출.
     */
    @Test
    void retrieve_whenRerankDisabled_returnsCandidatesWithoutRerank() {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        when(embeddingRepository.searchSimilar(any(), eq(10))).thenReturn(List.of(hitA, hitB));

        List<SearchHit> result = retriever(props(false)).retrieve("q");

        assertThat(result).containsExactly(hitA, hitB);
        verifyNoInteractions(reranker);
    }

    /**
     * rerank on: candidate-top-k(30)로 뽑아 Reranker.rerank(.., 6) 호출하고 그 결과를 반환.
     */
    @Test
    void retrieve_whenRerankEnabled_callsRerankerWithCandidates() {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        when(embeddingRepository.searchSimilar(any(), eq(30))).thenReturn(List.of(hitA, hitB));
        when(reranker.rerank(eq("q"), eq(List.of(hitA, hitB)), eq(6))).thenReturn(List.of(hitB));

        List<SearchHit> result = retriever(props(true)).retrieve("q");

        assertThat(result).containsExactly(hitB);
        verify(reranker).rerank("q", List.of(hitA, hitB), 6);
    }

    /**
     * rerank on + 후보 없음: Reranker 미호출, 빈 목록.
     */
    @Test
    void retrieve_whenRerankEnabledButNoCandidates_returnsEmpty() {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        when(embeddingRepository.searchSimilar(any(), eq(30))).thenReturn(List.of());

        List<SearchHit> result = retriever(props(true)).retrieve("q");

        assertThat(result).isEmpty();
        verify(reranker, never()).rerank(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
