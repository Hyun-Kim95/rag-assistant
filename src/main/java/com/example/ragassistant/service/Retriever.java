package com.example.ragassistant.service;

import com.example.ragassistant.config.RagProperties;
import com.example.ragassistant.domain.SearchHit;
import com.example.ragassistant.repository.EmbeddingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG retrieval: 질문을 embedding → pgvector top-k → min-score 필터.
 */
@Service
public class Retriever {

    private final EmbeddingService embeddingService;
    private final EmbeddingRepository embeddingRepository;
    private final RagProperties ragProperties;

    public Retriever(EmbeddingService embeddingService, EmbeddingRepository embeddingRepository, RagProperties ragProperties) {
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.ragProperties = ragProperties;
    }

    /**
     * @param question 사용자 질문
     * @return min-score 이상인 SearchHit 목록 (score 내림차순 유지)
     */
    public List<SearchHit> retrieve(String question) {
        float[] queryVector = embeddingService.embed(question);
        List<SearchHit> hits = embeddingRepository.searchSimilar(queryVector, ragProperties.topK());
        double minScore = ragProperties.minScore();

        return hits.stream()
                .filter(hit -> hit.getScore() >= minScore)
                .toList();
    }
}
