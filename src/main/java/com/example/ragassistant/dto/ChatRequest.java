package com.example.ragassistant.dto;

/**
 * POST /api/chat 요청 body
 *
 * @param question 사용자 질문 (필수, 공백 불가 — RagService에서 검증)
 */
public record ChatRequest(
        String question
) {
}
