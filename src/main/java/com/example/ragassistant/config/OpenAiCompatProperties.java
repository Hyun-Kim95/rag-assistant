package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.openai-compat")
public record OpenAiCompatProperties(
        boolean enabled,
        String name,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutMs
) {
}
