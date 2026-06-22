package com.example.ragassistant.agent;

import java.util.Map;

/**
 * 스트리밍 agent 루프의 이벤트 싱크.
 * 동기 경로(run)는 NOOP, /api/agent/stream은 SSE 중계 구현을 넘긴다.
 */
public interface AgentStreamHandler {

    /**
     * 도구 호출 시작(실행 전).
     */
    void onToolCall(int index, String tool, Map<String, Object> arguments);

    /**
     * 도구 결과(실행 후 요약).
     */
    void onToolResult(int index, String tool, String resultSummary);

    /**
     * 최종 답 토큰 조각.
     */
    void onDelta(String text);

    /**
     * 동기 경로용 무동작 싱크.
     */
    AgentStreamHandler NOOP = new AgentStreamHandler() {
        @Override
        public void onToolCall(int index, String tool, Map<String, Object> arguments) {
        }

        @Override
        public void onToolResult(int index, String tool, String resultSummary) {
        }

        @Override
        public void onDelta(String text) {
        }
    };
}
