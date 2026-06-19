package com.example.ragassistant.eval;

import java.time.Instant;
import java.util.List;

/**
 * 전체 실행 리포트
 */
public record EvalReport(
        String version,
        EvalMode mode,
        Instant ranAt,
        int totalScore,
        int maxTotalScore,
        List<EvalResult> results,
        String provider,            // 강제 provider 실행 시 leg 이름 (null = default 라우팅 단일 실행)
        long avgLatencyMs           // 문항 평균 응답 지연(ms)
) {
}
