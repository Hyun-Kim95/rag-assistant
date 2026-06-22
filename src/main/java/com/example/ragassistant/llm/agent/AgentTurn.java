package com.example.ragassistant.llm.agent;

import java.util.List;

/**
 * 모델 호출 1회의 결과.
 * - provider: 실제 응답한 leg 이름(응답 추적·표시용).
 * - content: 모델이 쓴 텍스트(직답 또는 tool 호출 시 동반 설명).
 * - toolCalls: 비어 있으면 '최종 답', 있으면 orchestrator가 도구를 실행하고 루프를 계속한다.
 */
public record AgentTurn(String provider, String content, List<ToolCall> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
