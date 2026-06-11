package com.example.ragassistant.domain;

import lombok.Getter;

/**
 * 검색 결과 1건 (chunk + 유사도 score).
 * vector-only: score = cosine similarity (1에 가까울수록 유사).
 * hybrid: 정렬은 RRF, score 필드는 출처 표시용으로
 * vector score 우선·없으면 lexical similarity.
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
