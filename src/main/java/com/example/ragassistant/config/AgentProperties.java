package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * agent.* 설정.
 * - maxSteps: 모델 호출(턴) 최대 반복. 기본 5.
 * - maxToolCalls: 전체 루프에서 실행하는 도구 호출 총량 상한(런어웨이 방지). 기본 10.
 * - timeoutMs: 요청당 전체 타임아웃. 기본 60000.
 * - providerOrder: 폴백 순서. 기본 [groq, ollama].
 * - maxHistoryTurns: 멀티턴 메모리 최근 대화 수. 기본 6.
 * - readMaxChunks/readMaxChars: read_document 청크 수/본문 길이 상한.
 * - summarizeMaxChars: summarize_document 본문 예산.
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        int maxSteps,
        int maxToolCalls,
        long timeoutMs,
        List<String> providerOrder,
        int maxHistoryTurns,
        int readMaxChunks,
        int readMaxChars,
        int summarizeMaxChars
) {
    public AgentProperties {
        if (maxSteps <= 0) {
            maxSteps = 5;
        }
        if (maxToolCalls <= 0) {
            maxToolCalls = 10;
        }
        if (timeoutMs <= 0) {
            timeoutMs = 60_000;
        }
        if (providerOrder == null || providerOrder.isEmpty()) {
            providerOrder = List.of("groq", "ollama");
        }
        if (maxHistoryTurns <= 0) {
            maxHistoryTurns = 6;
        }
        if (readMaxChunks <= 0) {
            readMaxChunks = 6;
        }
        if (readMaxChars <= 0) {
            readMaxChars = 4000;
        }
        if (summarizeMaxChars <= 0) {
            summarizeMaxChars = 8000;
        }
    }
}
