package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * agent.* 설정.
 * - maxSteps: tool 호출 루프 최대 반복(무한 루프 방지). 기본 5.
 * - timeoutMs: 요청당 전체 타임아웃. 기본 60000.
 * - providerOrder: 폴백 순서. 기본 [groq, ollama] (Groq 우선).
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        int maxSteps,
        long timeoutMs,
        List<String> providerOrder
) {
    public AgentProperties {
        if (maxSteps <= 0) {
            maxSteps = 5;
        }
        if (timeoutMs <= 0) {
            timeoutMs = 60_000;
        }
        if (providerOrder == null || providerOrder.isEmpty()) {
            providerOrder = List.of("groq", "ollama");
        }
    }
}
