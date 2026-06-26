package com.example.ragassistant.voice;

/**
 * 음성 통화 세션 상태머신.
 */
public enum CallState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    HANDOFF
}
