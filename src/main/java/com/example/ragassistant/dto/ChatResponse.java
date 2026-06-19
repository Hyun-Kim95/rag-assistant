package com.example.ragassistant.dto;

import java.util.List;

/**
 * POST /api/chat 응답
 *
 * @param answer   LLM 답변 (no-answer 시 고정 문구)
 * @param sources  검색에 사용된 chunk 출처 (no-answer면 빈 리스트)
 * @param grounded true = context 기반 답변, false = no-answer
 * @param provider 실제 답변을 생성한 chat provider 이름 (LLM 미호출 시 null)
 */
public record ChatResponse(
        String answer,
        List<SourceCitation> sources,
        boolean grounded,
        String provider
) {
    /**
     * provider를 모르거나 불필요할 때(평가·테스트 등) 쓰는 편의 생성자 → provider=null
     */
    public ChatResponse(String answer, List<SourceCitation> sources, boolean grounded) {
        this(answer, sources, grounded, null);
    }

    /**
     * 검색 결과 없거나 min-score 미달 시 (LLM 미호출)
     */
    public static ChatResponse noAnswer(String message) {
        return new ChatResponse(message, List.of(), false, null);
    }
}
