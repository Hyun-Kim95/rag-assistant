package com.example.ragassistant.service;

import com.example.ragassistant.config.RagProperties;
import com.example.ragassistant.domain.SearchHit;
import com.example.ragassistant.observability.QueryTelemetryContext;
import com.example.ragassistant.repository.ChunkRepository;
import com.example.ragassistant.repository.EmbeddingRepository;
import com.example.ragassistant.search.Reranker;
import com.example.ragassistant.search.RrfFusion;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG retrieval:
 * - rerank off: 기존 동작 (vector-only 또는 hybrid → top-k = context)
 * - rerank on : 넓은 후보(candidate-top-k) → TEI rerank → rerank-top-n = context
 * min-score는 후보 단계(cosine/lexical)에만 적용. rerank score엔 임계 없음.
 */
@Service
public class Retriever {

    private final EmbeddingService embeddingService;
    private final EmbeddingRepository embeddingRepository;
    private final ChunkRepository chunkRepository;
    private final RagProperties ragProperties;
    private final Reranker reranker;
    private final QueryTelemetryContext telemetry;

    public Retriever(EmbeddingService embeddingService, EmbeddingRepository embeddingRepository, ChunkRepository chunkRepository, RagProperties ragProperties, Reranker reranker, QueryTelemetryContext telemetry) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.chunkRepository = chunkRepository;
        this.ragProperties = ragProperties;
        this.reranker = reranker;
        this.telemetry = telemetry;
    }

    private static long msSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * RAG 질의의 최종 retrieval 진입점.
     * rerank-enabled에 따라 분기한다.
     * - off: top-k 후보를 그대로 context로 반환 (기존 동작·회귀 대조용).
     * - on : candidate-top-k로 넓게 뽑은 후보를 TEI로 rerank해 상위 rerank-top-n만 반환.
     * 후보가 비면 빈 목록 → 호출 측(RagService)에서 no-answer 처리.
     *
     * @param question 사용자 질문 (trim된 상태로 전달받음)
     * @return context로 쓸 SearchHit 목록 (rerank on이면 rerank score·순서)
     */
    public List<SearchHit> retrieve(String question) {
        if (!ragProperties.rerankEnabled()) {
            // 기존 동작 그대로: top-k 후보가 곧 context
            return retrieveCandidates(question, ragProperties.topK(), ragProperties.lexicalTopK());
        }
        // rerank 파이프라인: 넓은 후보 → rerank → top-n
        List<SearchHit> candidates = retrieveCandidates(
                question, ragProperties.candidateTopK(), ragProperties.candidateTopK());
        if (candidates.isEmpty()) {
            return List.of();   // 후보 없음 → no-answer (RagService에서 처리)
        }
        long t0 = System.nanoTime();
        List<SearchHit> reranked = reranker.rerank(question, candidates, ragProperties.rerankTopN());
        telemetry.recordRerankMs(msSince(t0));
        return reranked;
    }

    /**
     * rerank candidate를 모으는 공통 경로.
     * hybrid-enabled면 vector+lexical 병합(RRF), 아니면 vector-only.
     * 호출 측이 limit을 주입하므로 rerank on(넓게)·off(top-k)·debug(top-k)에서 재사용된다.
     *
     * @param question     질문
     * @param vectorLimit  vector leg 후보 수
     * @param lexicalLimit lexical leg 후보 수 (hybrid일 때만 사용)
     * @return min-score를 통과한 후보 목록
     */
    private List<SearchHit> retrieveCandidates(String question, int vectorLimit, int lexicalLimit) {
        if (ragProperties.hybridEnabled()) {
            return retrieveHybrid(question, vectorLimit, lexicalLimit);
        }
        return retrieveVectorOnly(question, vectorLimit);
    }

    /**
     * embed → cosine top-k → min-score
     */
    private List<SearchHit> retrieveVectorOnly(String question, int limit) {
        long tEmbed = System.nanoTime();
        float[] queryVector = embeddingService.embedQuery(question);
        telemetry.recordEmbeddingMs(msSince(tEmbed));

        long tSearch = System.nanoTime();
        List<SearchHit> hits = embeddingRepository.searchSimilar(queryVector, limit);
        telemetry.recordRetrievalMs(msSince(tSearch));
        return filterByVectorMinScore(hits);
    }

    /**
     * Hybrid: 두 leg를 독립 후보 풀에서 가져온 뒤 RRF로 순위만 합친다.
     * min-score 정책:
     * - vector leg: cosine ≥ rag.min-score
     * - lexical leg: similarity ≥ rag.lexical-min-score
     * - 병합 후: 두 leg 모두에서 걸러진 후보만 RRF 입력
     * → lexical-only로 끌어올린 chunk는 vector score 0이어도 lexical threshold를 통과해야 함.
     */
    private List<SearchHit> retrieveHybrid(String question, int vectorLimit, int lexicalLimit) {
        long tEmbed = System.nanoTime();
        float[] queryVector = embeddingService.embedQuery(question);
        telemetry.recordEmbeddingMs(msSince(tEmbed));

        long tSearch = System.nanoTime();
        List<SearchHit> vectorHits = filterByVectorMinScore(
                embeddingRepository.searchSimilar(queryVector, vectorLimit)
        );
        List<SearchHit> lexicalHits = chunkRepository.searchLexical(
                question, lexicalLimit, ragProperties.lexicalMinScore()
        );
        if (vectorHits.isEmpty() && lexicalHits.isEmpty()) {
            telemetry.recordRetrievalMs(msSince(tSearch));
            return List.of();
        }
        List<SearchHit> fused = RrfFusion.fuse(
                vectorHits, lexicalHits,
                ragProperties.rrfK(),
                Math.max(vectorLimit, lexicalLimit)   // 후보 폭만큼 RRF 출력
        );
        telemetry.recordRetrievalMs(msSince(tSearch));
        return fused;
    }

    /**
     * vector leg의 cosine min-score 필터.
     * lexical leg는 SQL(WHERE similarity ≥ ?)에서 이미 걸러지므로 여기선 vector만 처리한다.
     *
     * @param hits cosine 검색 결과
     * @return rag.min-score 이상만 남긴 목록
     */
    private List<SearchHit> filterByVectorMinScore(List<SearchHit> hits) {
        double minScore = ragProperties.minScore();
        return hits.stream()
                .filter(hit -> hit.getScore() >= minScore)
                .toList();
    }

    // local 비교 API용
    // hybrid-enabled와 관계없이 vector-only 결과
    public List<SearchHit> retrieveVectorOnlyForDebug(String question) {
        return retrieveVectorOnly(question, ragProperties.topK());
    }

    // hybrid-enabled와 관계없이 hybrid 결과
    public List<SearchHit> retrieveHybridForDebug(String question) {
        return retrieveHybrid(question, ragProperties.topK(), ragProperties.lexicalTopK());
    }
}
