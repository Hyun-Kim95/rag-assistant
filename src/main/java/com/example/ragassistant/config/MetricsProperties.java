package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 지표 기능 토글 + provider별 토큰 단가.
 * 단가는 "운영 비용 추정"용이며 사용자 과금/청구와 무관하다(product-monetization-default).
 */
@ConfigurationProperties(prefix = "metrics")
public record MetricsProperties(
        boolean enabled,
        Cost cost
) {
    public record Cost(
            String currency,
            // key = provider 이름(telemetry.recordProvider 값과 일치: ollama-7b/ollama-1b/groq ...)
            Map<String, ProviderRate> providers
    ) {
    }

    public record ProviderRate(
            double promptPer1k,         // yaml: prompt-per-1k (1K 토큰당 단가)
            double completionPer1k      // yaml: completion-per-1k
    ) {
    }

    public String currencyOrDefault() {
        return cost != null && cost.currency() != null ? cost.currency() : "USD";
    }

    /**
     * 해당 provider 단가. 미설정이면 null → 비용 추정 생략(=null 저장).
     */
    public ProviderRate rateFor(String provider) {
        if (cost == null || cost.providers() == null || provider == null) {
            return null;
        }
        return cost.providers().get(provider);
    }
}
