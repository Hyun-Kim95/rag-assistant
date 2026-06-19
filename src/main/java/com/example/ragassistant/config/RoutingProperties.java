package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * llm.default-provider / llm.fallback-order 바인딩.
 * 라우터의 1회 순회 체인 = [defaultProvider, ...fallbackOrder] (중복 제거).
 */
@ConfigurationProperties(prefix = "llm")
public record RoutingProperties(
        String defaultProvider,
        List<String> fallbackOrder
) {
}
