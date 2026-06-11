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
        int rrfK
) {
}
