package com.example.ragassistant.dto;

import java.util.List;

/**
 * POST /api/chat 응답
 *
 * @param answer   LLM 답변 (no-answer 시 고정 문구)
 * @param sources  검색에 사용된 chunk 출처 (no-answer면 빈 리스트)
 * @param grounded true = context 기반 답변, false = no-answer
 */
public record ChatResponse(
        String answer,
        List<SourceCitation> sources,
        boolean grounded
) {
    /**
     * 검색 결과 없거나 min-score 미달 시
     */
    public static ChatResponse noAnswer(String message) {
        return new ChatResponse(message, List.of(), false);
    }
}
