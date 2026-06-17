package com.example.ragassistant.eval;

import java.util.List;

/**
 * eval/questions.json 의 문항 1건.
 * 채점은 EvalScorer가 이 필드만 보고 결정적으로 수행
 */
public record EvalQuestion(
        int id,
        String category,           // A_FACT | B_POLICY | C_NO_ANSWER
        String question,
        int maxScore,
        boolean expectNoAnswer,
        boolean mustGrounded,
        Integer minSourceCount,    // null이면 검사 생략
        Integer maxSourceCount,
        List<String> mustContainAll,
        List<String> mustContainAny,
        List<String> mustNotContain
) {
    public List<String> mustContainAll() {
        return mustContainAll != null ? mustContainAll : List.of();
    }
    public List<String> mustContainAny() {
        return mustContainAny != null ? mustContainAny : List.of();
    }
    public List<String> mustNotContain() {
        return mustNotContain != null ? mustNotContain : List.of();
    }
}
