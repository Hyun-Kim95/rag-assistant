package com.example.ragassistant.eval;

import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.service.PromptBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM-as-judge 없이 룰 기반 채점.
 * - 2점: 모든 필수 조건 충족
 * - 1점: 부분 충족 (키워드 일부, grounded는 OK 등)
 * - 0점: 금지어·반대 케이스·no-answer 실패
 * v2 수동 채점과 100% 일치시키긴 어렵지만, 회귀·추세 비교에는 충분하다.
 */
@Component
public class EvalScorer {
    public EvalResult score(EvalQuestion q, EvalMode mode, ChatResponse response) {
        String answer = response.answer() != null ? response.answer() : "";
        boolean noAnswer = isNoAnswer(answer, response.grounded());
        List<String> fails = new ArrayList<>();
        // --- no-answer 문항 (8~10) ---
        if (q.expectNoAnswer()) {
            if (response.grounded()) fails.add("expectNoAnswer but grounded=true");
            if (!response.sources().isEmpty()) fails.add("expectNoAnswer but sources not empty");
            if (!noAnswer) fails.add("expectNoAnswer but answer is not no-answer phrase");
        } else {
            // --- 사실·정책 문항 (1~7) ---
            if (q.mustGrounded() && !response.grounded()) {
                fails.add("mustGrounded but grounded=false");
            }
            if (q.minSourceCount() != null && response.sources().size() < q.minSourceCount()) {
                fails.add("sources below minSourceCount");
            }
        }
        if (q.maxSourceCount() != null && response.sources().size() > q.maxSourceCount()) {
            fails.add("sources above maxSourceCount");
        }
        for (String forbidden : q.mustNotContain()) {
            if (answer.contains(forbidden)) {
                fails.add("forbidden: " + forbidden);
            }
        }
        int keywordScore = scoreKeywords(answer, q);
        int score = decideScore(q, fails, keywordScore, noAnswer);
        return new EvalResult(
                q.id(), q.category(), q.question(), mode,
                answer, response.grounded(), response.sources(),
                noAnswer, score, q.maxScore(), fails
        );
    }
    private boolean isNoAnswer(String answer, boolean grounded) {
        if (grounded) return false;
        String trimmed = answer.strip();
        return trimmed.startsWith(PromptBuilder.NO_ANSWER_MESSAGE);
    }
    private int scoreKeywords(String answer, EvalQuestion q) {
        int all = 0, any = 0;
        for (String k : q.mustContainAll()) {
            if (answer.contains(k)) all++;
        }
        if (!q.mustContainAll().isEmpty() && all == q.mustContainAll().size()) return 2;
        if (!q.mustContainAny().isEmpty()) {
            for (String k : q.mustContainAny()) {
                if (answer.contains(k)) any++;
            }
            if (any >= Math.max(1, q.mustContainAny().size() / 2)) return 2;
            if (any > 0) return 1;
        }
        if (!q.mustContainAll().isEmpty() && all > 0) return 1;
        if (q.mustContainAll().isEmpty() && q.mustContainAny().isEmpty()) return 2;
        return 0;
    }
    private int decideScore(EvalQuestion q, List<String> fails, int keywordScore, boolean noAnswer) {
        // 치명적 실패 → 0
        if (!fails.isEmpty()) return 0;
        if (q.expectNoAnswer()) {
            return noAnswer ? q.maxScore() : 0;
        }
        return Math.min(q.maxScore(), keywordScore);
    }
}
