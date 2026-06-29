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

    /**
     * STT 모드 안내(연결 직후 1회). text="cloud"면 클라이언트가 오디오를 녹음·전송, "browser"면 텍스트만 전송.
     */
    public static VoiceEvent sttMode(String mode) {
        return new VoiceEvent("stt.mode", mode, null, null, null, null, null, null, null);
    }

    /**
     * 클라우드 STT 확정 전사. 클라이언트가 직전 사용자 말풍선 텍스트를 이 값으로 보정한다.
     */
    public static VoiceEvent sttFinal(String text) {
        return new VoiceEvent("stt.final", text, null, null, null, null, null, null, null);
    }
}
