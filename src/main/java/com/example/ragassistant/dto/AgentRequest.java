package com.example.ragassistant.dto;

import java.util.List;

/**
 * POST /api/agent 요청.
 *
 * @param message  사용자 입력(필수, 공백 불가).
 * @param provider (선택) 우선 사용할 agent provider. null이면 agent.provider-order 라우팅.
 * @param messages (선택) 이전 대화(멀티턴 메모리, 무상태). null/생략이면 단발(기존과 동일).
 */
public record AgentRequest(String message, String provider, List<ConversationTurn> messages) {
    public AgentRequest {
        if (messages == null) {
            messages = List.of();   // 구버전 클라이언트({message, provider})와 하위호환
        }
    }
}
