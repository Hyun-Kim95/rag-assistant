package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        String baseUrl,
        String chatModel,
        String smallChatModel,
        String classifierModel,
        String embeddingModel,
        Double temperature,
        Integer timeoutMs       // chat/embedding read timeout(ms). null·<=0 → 기본 120000. 느린 로컬 모델 콜드 로드 시 상향
) {
    public int readTimeoutMsOrDefault() {
        return (timeoutMs != null && timeoutMs > 0) ? timeoutMs : 120_000;
    }
}
