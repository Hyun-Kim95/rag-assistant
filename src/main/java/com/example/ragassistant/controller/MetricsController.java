package com.example.ragassistant.controller;

import com.example.ragassistant.config.MetricsProperties;
import com.example.ragassistant.dto.ErrorResponse;
import com.example.ragassistant.dto.MetricsSummaryResponse;
import com.example.ragassistant.dto.MetricsTimeseriesResponse;
import com.example.ragassistant.metrics.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 지표 조회. 로컬 비인증. 운영 노출 시 권한 가드는 별도 합의.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService service;
    private final MetricsProperties props;

    public MetricsController(MetricsService service, MetricsProperties props) {
        this.service = service;
        this.props = props;
    }

    /**
     * @param from     ISO-8601 LocalDateTime(예: 2026-06-01T00:00:00). 생략 시 최근 7일.
     * @param to       생략 시 현재.
     * @param channel  chat | agent | voice | all(기본).
     * @param provider 선택. 특정 provider만 필터.
     */
    @GetMapping("/summary")
    public ResponseEntity<?> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "all") String channel,
            @RequestParam(required = false) String provider) {

        // 기능 비활성 → 404
        if (!props.enabled()) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("METRICS_DISABLED", "메트릭 기능이 비활성화되어 있습니다."));
        }

        LocalDateTime fromTs = (from != null) ? LocalDateTime.parse(from) : LocalDateTime.now().minusDays(7);
        LocalDateTime toTs = (to != null) ? LocalDateTime.parse(to) : LocalDateTime.now();

        // from > to → 400. 기존 IllegalArgumentException 매핑 재사용.
        if (fromTs.isAfter(toTs)) {
            throw new IllegalArgumentException("from이 to보다 늦습니다.");
        }

        MetricsSummaryResponse body = service.summary(fromTs, toTs, channel, provider);
        return ResponseEntity.ok(body);
    }

    private static final Set<String> ALLOWED_BUCKETS = Set.of("hour", "day", "week");

    /**
     * 추이(드리프트). 기간을 bucket(hour|day|week) 단위로 쪼개 품질·지연·토큰·검색점수 시계열을 반환.
     *
     * @param from     생략 시 최근 14일.
     * @param to       생략 시 현재.
     * @param channel  chat | agent | voice | all(기본). voice는 query_logs 대상이 아니라 빈 추이.
     * @param provider 선택.
     * @param bucket   day(기본) | week | hour.
     */
    @GetMapping("/timeseries")
    public ResponseEntity<?> timeseries(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "all") String channel,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false, defaultValue = "day") String bucket) {

        if (!props.enabled()) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("METRICS_DISABLED", "메트릭 기능이 비활성화되어 있습니다."));
        }
        if (!ALLOWED_BUCKETS.contains(bucket)) {
            throw new IllegalArgumentException("bucket은 hour|day|week 중 하나여야 합니다.");
        }

        LocalDateTime fromTs = (from != null) ? LocalDateTime.parse(from) : LocalDateTime.now().minusDays(14);
        LocalDateTime toTs = (to != null) ? LocalDateTime.parse(to) : LocalDateTime.now();
        if (fromTs.isAfter(toTs)) {
            throw new IllegalArgumentException("from이 to보다 늦습니다.");
        }

        MetricsTimeseriesResponse body = service.timeseries(fromTs, toTs, channel, provider, bucket);
        return ResponseEntity.ok(body);
    }
}
