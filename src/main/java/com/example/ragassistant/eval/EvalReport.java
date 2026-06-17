package com.example.ragassistant.eval;

import java.time.Instant;
import java.util.List;

/** 전체 실행 리포트 */
public record EvalReport(
        String version,
        EvalMode mode,
        Instant ranAt,
        int totalScore,
        int maxTotalScore,
        List<EvalResult> results
) {}
