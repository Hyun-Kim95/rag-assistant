package com.example.ragassistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SSE data payload (event 이름은 SseEmitter.event().name(...)으로 구분).
 * delta: {"text":"..."}
 * done:  ChatResponse와 같은 필드 (answer, sources, grounded)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEvent(
        String text,
        String answer,
        java.util.List<SourceCitation> sources,
        Boolean grounded
) {
    public static ChatStreamEvent delta(String text) {
        return new ChatStreamEvent(text, null, null, null);
    }
    public static ChatStreamEvent done(ChatResponse response) {
        return new ChatStreamEvent(
                null,
                response.answer(),
                response.sources(),
                response.grounded()
        );
    }
}
