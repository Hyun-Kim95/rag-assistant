package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        String baseUrl,
        String chatModel,
        String smallChatModel,
        String classifierModel,
        String embeddingModel,
        Double temperature
) {
}
