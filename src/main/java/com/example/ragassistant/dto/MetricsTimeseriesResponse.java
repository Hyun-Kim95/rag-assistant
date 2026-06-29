package com.example.ragassistant.dto;

import java.util.List;

/**
 * GET /api/metrics/timeseries 응답. 기간을 bucket 단위로 쪼갠 추이(드리프트 감시용).
 * 각 지표는 데이터 없는 버킷에선 null(빈 상태).
 */
public record MetricsTimeseriesResponse(
        Range range,
        String channel,
        String bucket,
        List<Point> points
) {
    public record Range(String from, String to) {
    }

    public record Point(
            String bucketStart,
            long interactions,
            Double groundedRate,
            Double noAnswerRate,
            Double p95,
            Double avgTokens,
            Double avgTopScore,
            Double totalCost
    ) {
    }
}
