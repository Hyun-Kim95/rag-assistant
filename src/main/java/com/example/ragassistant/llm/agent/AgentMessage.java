package com.example.ragassistant.llm.agent;

import java.util.List;

/**
 * agent 루프에서 주고받는 대화 메시지 1건 (provider 중립).
 * - role: "system" | "user" | "assistant" | "tool"
 * - content: 본문. assistant가 tool만 호출하면 null/빈 문자열일 수 있다.
 * - toolCalls: assistant가 호출한 도구 목록 (assistant 메시지에만).
 * - toolCallId: 이 메시지가 어떤 tool_call에 대한 결과인지 (tool 메시지에만).
 * provider별 wire 포맷 차이(arguments string vs object 등)는 각 AgentChatClient가 변환한다.
 */
public record AgentMessage(
        String role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId
) {
    public static AgentMessage system(String content) {
        return new AgentMessage("system", content, List.of(), null);
    }

    public static AgentMessage user(String content) {
        return new AgentMessage("user", content, List.of(), null);
    }

    /**
     * 모델이 돌려준 assistant 턴(직답 또는 tool 호출)을 히스토리에 다시 넣을 때
     */
    public static AgentMessage assistant(String content, List<ToolCall> toolCalls) {
        return new AgentMessage("assistant", content, toolCalls == null ? List.of() : toolCalls, null);
    }

    /**
     * 도구 실행 결과를 모델에 되돌릴 때
     */
    public static AgentMessage tool(String toolCallId, String content) {
        return new AgentMessage("tool", content, List.of(), toolCallId);
    }
}
