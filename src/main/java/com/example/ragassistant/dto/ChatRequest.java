package com.example.ragassistant.dto;

/**
 * POST /api/chat 요청 body
 *
 * @param question 사용자 질문 (필수, 공백 불가 — RagService에서 검증)
 * @param provider (선택) 우선 사용할 chat provider 이름. null이면 default-provider 라우팅.
 *                 등록되지 않은 이름이면 400 UNKNOWN_PROVIDER.
 */
public record ChatRequest(
        String question,
        String provider
) {
}
