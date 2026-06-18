package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        int chunkSize,
        int chunkOverlap,
        int topK,
        double minScore,
        int embeddingDimension,
        // --- hybrid ---
        boolean hybridEnabled,
        int lexicalTopK,
        double lexicalMinScore,
        int rrfK,
        // --- rerank (2단계 retrieval) ---
        int candidateTopK,   // rerank 전 넓게 뽑는 후보 수 (vector·lexical 두 leg 공통)
        int rerankTopN,      // rerank 후 context로 쓸 상위 개수
        boolean rerankEnabled // false면 기존(topK) 동작 그대로 — 회귀 대조·안전장치
) {
}
