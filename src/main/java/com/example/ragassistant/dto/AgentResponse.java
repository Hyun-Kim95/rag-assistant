package com.example.ragassistant.dto;

import java.util.List;

/**
 * POST /api/agent 응답.
 *
 * @param answer     최종 답변.
 * @param sources    답변 근거로 쓰인 출처(검색 도구가 만든 것, 중복 제거).
 * @param grounded   출처 기반 답인지(sources 비어있지 않으면 true).
 * @param provider   실제 답을 만든 agent provider.
 * @param stopReason FINAL | MAX_STEPS | TIMEOUT.
 * @param steps      도구 호출 추적(없으면 빈 리스트).
 */
public record AgentResponse(
        String answer,
        List<SourceCitation> sources,
        boolean grounded,
        String provider,
        String stopReason,
        List<AgentStep> steps
) {
}
