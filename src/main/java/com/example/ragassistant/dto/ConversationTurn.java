package com.example.ragassistant.dto;

/**
 * 멀티턴 메모리(무상태)용 이전 대화 1건. 클라이언트가 AgentRequest.messages로 보낸다.
 * - role: "user" | "assistant" (그 외 role은 서버가 무시 → system 주입 등 인젝션 방지).
 * - content: 본문.
 */
public record ConversationTurn(String role, String content) {
}
