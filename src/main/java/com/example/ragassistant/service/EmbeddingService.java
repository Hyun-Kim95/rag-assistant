package com.example.ragassistant.service;

import com.example.ragassistant.config.RagProperties;
import com.example.ragassistant.llm.EmbeddingModelClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * chunk 텍스트 → embedding 벡터 변환.
 * Ollama HTTP 호출은 OllamaService에 위임하고,
 * 여기서는 RAG 정책(빈 텍스트, dimension 검증)만 담당한다.
 */
@Service
public class EmbeddingService {

    private final EmbeddingModelClient ollamaService;
    private final RagProperties ragProperties;

    public EmbeddingService(EmbeddingModelClient ollamaService, RagProperties ragProperties) {
        this.ollamaService = ollamaService;
        this.ragProperties = ragProperties;
    }

    /**
     * @return float 배열 (pgvector INSERT용). 빈/공백 텍스트는 예외.
     */
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("embedding 대상 텍스트가 비어 있습니다.");
        }
        List<Double> vector = ollamaService.embed(text.trim());
        int expected = ragProperties.embeddingDimension();
        if (vector.size() != expected) {
            throw new IllegalStateException(
                    "embedding dimension 불일치: expected=" + expected + ", actual=" + vector.size()
            );
        }
        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).floatValue();
        }
        return result;
    }
}
