package com.example.ragassistant.dto;

import java.util.Map;

/**
 * GET /api/metrics/summary 응답. 데이터 없는 구간은 null(빈 상태).
 */
public record MetricsSummaryResponse(
        Range range,
        String channel,
        Counts counts,
        Quality quality,
        Latency latency,
        Cost cost,
        Reliability reliability,
        Map<String, NorthStar> northStar
) {
    public record Range(String from, String to) {
    }

    public record Counts(long interactions, long sessions) {
    }

    public record Quality(Double groundedRate, Double noAnswerRate) {
    }

    public record Latency(Percentiles total, StageP95 stage) {
    }

    public record Percentiles(Double p50, Double p95, Double p99) {
    }

    public record StageP95(Double embedP95, Double retrieveP95, Double rerankP95, Double genP95) {
    }

    public record Cost(String currency, Tokens tokensPerInteraction, Double costPerInteraction,
                       Map<String, ProviderCost> byProvider) {
    }

    public record Tokens(Double avg, Double p95) {
    }

    public record ProviderCost(long interactions, Double costPerInteraction) {
    }

    public record Reliability(Double handoffRate, Double taskCompletionRate) {
    }

    public record NorthStar(String metric, Double value) {
    }
}
