package com.example.ragassistant.dto;

/**
 * POST /api/agent 요청.
 *
 * @param message  사용자 입력(필수, 공백 불가).
 * @param provider (선택) 우선 사용할 agent provider. null이면 agent.provider-order 라우팅.
 */
public record AgentRequest(String message, String provider) {
}
