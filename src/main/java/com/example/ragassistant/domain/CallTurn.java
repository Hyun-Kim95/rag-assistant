package com.example.ragassistant.domain;

/**
 * 통화 한 턴의 로그(저장 전용). user_text_masked는 PiiMasker 적용 후 값.
 * 지연(ms)은 측정 불가 시 null이 아닌 0으로 채운다.
 */
public record CallTurn(
        Long sessionId,
        int turnIndex,
        String userTextMasked,
        String answerText,
        boolean grounded,
        Integer sttMs,
        Integer llmMs,
        Integer ttsMs,
        Integer ttfbMs
) {
}
