package com.example.ragassistant.eval;

import com.example.ragassistant.dto.SourceCitation;

import java.util.List;

/** 문항 1건 실행 + 채점 결과 */
public record EvalResult(
        int id,
        String category,
        String question,
        EvalMode mode,
        String answer,
        boolean grounded,
        List<SourceCitation> sources,
        boolean noAnswer,          // 앱이 no-answer로 처리했는지 (grounded=false + 고정 문구)
        int score,
        int maxScore,
        List<String> failReasons   // 채점 실패 이유 (리포트·디버깅용)
) {}
