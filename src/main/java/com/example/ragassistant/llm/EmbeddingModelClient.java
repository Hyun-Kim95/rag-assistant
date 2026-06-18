package com.example.ragassistant.llm;

import java.util.List;

/**
 * 임베딩 추론 경계(transport). dimension 검증 등 RAG 정책은 포함하지 않는다(EmbeddingService 담당).
 * chat과 분리한 이유: chat·embedding은 모델·스케일·라우팅 관심사가 다르다
 * (예: chat만 외부로 빼고 embedding은 로컬 유지).
 */
public interface EmbeddingModelClient {

    /**
     * 텍스트 → 임베딩 벡터(raw, 모델 출력 차원 그대로)
     */
    List<Double> embed(String text);
}
