package com.example.ragassistant.voice;

import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.dto.SourceCitation;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * WebSocket(/ws/voice) 서버 송신 이벤트. event 필드로 종류 구분.
 * null 필드는 직렬화에서 제외해 페이로드를 가볍게 한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VoiceEvent(
        String event,
        String text,
        String answer,
        List<SourceCitation> sources,
        Boolean grounded,
        String state,
        String reason,
        String error,
        String message
) {
    public static VoiceEvent delta(String text) {
        return new VoiceEvent("answer.delta", text, null, null, null, null, null, null, null);
    }

    public static VoiceEvent done(ChatResponse r) {
        return new VoiceEvent("answer.done", null, r.answer(), r.sources(), r.grounded(), null, null, null, null);
    }

    public static VoiceEvent state(CallState s) {
        return new VoiceEvent("state", null, null, null, null, s.name(), null, null, null);
    }

    public static VoiceEvent handoff(String reason) {
        return new VoiceEvent("handoff", null, null, null, null, null, reason, null, null);
    }

    public static VoiceEvent error(String code, String message) {
        return new VoiceEvent("error", null, null, null, null, null, null, code, message);
    }

    public static VoiceEvent ttsFallback(String text) {
        return new VoiceEvent("tts.fallback", text, null, null, null, null, null, null, null);
    }

    /**
     * 검색 등 처리 시작을 알리는 짧은 안내(filler). 본 답변 전에 먼저 들려준다.
     */
    public static VoiceEvent notice(String text) {
        return new VoiceEvent("notice", text, null, null, null, null, null, null, null);
    }
}
