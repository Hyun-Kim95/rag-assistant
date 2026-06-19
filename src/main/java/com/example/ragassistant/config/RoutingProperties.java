package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * llm.* 라우팅 설정.
 * - routing-strategy: "fixed"(기본) = [default, ...fallback] 고정 체인.
 * "difficulty" = 난이도 분류로 1차 leg 선택 후 동일 폴백.
 * - difficulty: tier→provider 매핑(difficulty 전략에서만 사용).
 */
@ConfigurationProperties(prefix = "llm")
public record RoutingProperties(
        String defaultProvider,
        List<String> fallbackOrder,
        String routingStrategy,
        Difficulty difficulty
) {
    public RoutingProperties {
        if (routingStrategy == null || routingStrategy.isBlank()) {
            routingStrategy = "fixed";  // 미설정 시 기존 동작 보존
        }
    }

    /**
     * difficulty 전략의 tier→provider 매핑.
     */
    public record Difficulty(String easyProvider, String hardProvider) {
    }
}
