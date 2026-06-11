package com.example.ragassistant.service;

import com.example.ragassistant.config.RagProperties;
import com.example.ragassistant.domain.SearchHit;
import com.example.ragassistant.repository.ChunkRepository;
import com.example.ragassistant.repository.EmbeddingRepository;
import com.example.ragassistant.search.RrfFusion;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG retrieval:
 * - 기본(vector-only): 질문 embed → pgvector cosine top-k → min-score
 * - hybrid: vector leg + lexical leg(pg_trgm) → RRF 병합 → leg별 min-score
 */
@Service
public class Retriever {

    private final EmbeddingService embeddingService;
    private final EmbeddingRepository embeddingRepository;
    private final ChunkRepository chunkRepository;
    private final RagProperties ragProperties;

    public Retriever(EmbeddingService embeddingService, EmbeddingRepository embeddingRepository, ChunkRepository chunkRepository, RagProperties ragProperties) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.chunkRepository = chunkRepository;
        this.ragProperties = ragProperties;
    }

    /**
     * hybrid-enabled에 따라 vector-only 또는 hybrid.
     * @param question 사용자 질문
     * @return min-score 이상인 SearchHit 목록 (score 내림차순 유지)
     */
    public List<SearchHit> retrieve(String question) {
        if (ragProperties.hybridEnabled()) {
            return retrieveHybrid(question);
        }
        return retrieveVectorOnly(question);
    }

    /** embed → cosine top-k → min-score */
    private List<SearchHit> retrieveVectorOnly(String question) {
        float[] queryVector = embeddingService.embed(question);
        List<SearchHit> hits = embeddingRepository.searchSimilar(queryVector, ragProperties.topK());
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
    private List<SearchHit> retrieveHybrid(String question) {

        float[] queryVector = embeddingService.embed(question);

        List<SearchHit> vectorHits = filterByVectorMinScore(
                embeddingRepository.searchSimilar(queryVector, ragProperties.topK())
        );
        List<SearchHit> lexicalHits = chunkRepository.searchLexical(
                question,
                ragProperties.lexicalTopK(),
                ragProperties.lexicalMinScore()
        );

        if (vectorHits.isEmpty() && lexicalHits.isEmpty()) {
            return List.of();
        }

        return RrfFusion.fuse(
                vectorHits,
                lexicalHits,
                ragProperties.rrfK(),
                ragProperties.topK()
        );
    }

    private List<SearchHit> filterByVectorMinScore(List<SearchHit> hits) {
        double minScore = ragProperties.minScore();
        return hits.stream()
                .filter(hit -> hit.getScore() >= minScore)
                .toList();
    }

    // local 비교 API용
    // hybrid-enabled와 관계없이 vector-only 결과
    public List<SearchHit> retrieveVectorOnlyForDebug(String question) {
        return retrieveVectorOnly(question);
    }
    // hybrid-enabled와 관계없이 hybrid 결과
    public List<SearchHit> retrieveHybridForDebug(String question) {
        return retrieveHybrid(question);
    }
}
