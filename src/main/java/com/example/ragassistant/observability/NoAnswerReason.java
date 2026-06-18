package com.example.ragassistant.observability;

/**
 * no-answer가 난 이유 (관측 로그용).
 * null = 정상 답변(grounded).
 */
public enum NoAnswerReason {
    /** 후보가 비어 LLM을 호출하지 않음 (검색 0건 또는 min-score 미달) */
    EMPTY_HITS,
    /** LLM이 호출됐으나 근거 없음으로 판별됨 (grounded=false) */
    LLM_NO_ANSWER
}
