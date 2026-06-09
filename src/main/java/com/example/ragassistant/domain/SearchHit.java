package com.example.ragassistant.domain;

import lombok.Getter;

/**
 * pgvector 유사도 검색 결과 1건.
 * document_chunks + document_embeddings JOIN 결과.
 */
@Getter
public class SearchHit {

    private final Long chunkId;
    private final String documentName;
    private final String content;
    // cosine similarity (1에 가까울수록 유사). min-score 필터에 사용
    private final double score;

    public SearchHit(Long chunkId, String documentName, String content, double score) {
        this.chunkId = chunkId;
        this.documentName = documentName;
        this.content = content;
        this.score = score;
    }
}
