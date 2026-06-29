package com.example.ragassistant.observability;

import java.time.LocalDateTime;

/**
 * query_logs 1행에 적재할 불변 스냅샷. QueryTelemetry(가변 수집기)에서 한 번 떠낸 값.
 */
public record QueryLog(
        String requestId,
        String channel,
        String provider,
        boolean grounded,
        String noAnswerReason,   // enum name 또는 null
        int hitCount,
        Double topScore,
        long embedMs,
        long retrieveMs,
        long rerankMs,
        long genMs,
        long totalMs,
        Boolean rerankFallback,
        Integer promptTokens,
        Integer completionTokens,
        String stopReason,
        LocalDateTime createdAt
) {
}
