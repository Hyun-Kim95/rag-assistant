package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 음성(TTS/STT) 설정.
 * - TTS 서비스계정 키는 credentials-path 또는 GOOGLE_APPLICATION_CREDENTIALS.
 * - STT(Groq Whisper)는 base-url/model만 여기서 두고, api-key는 llm.openai-compat.api-key(Groq)를 재사용한다.
 */
@ConfigurationProperties(prefix = "voice")
public record VoiceProperties(Tts tts, Stt stt) {

    public VoiceProperties {
        if (tts == null) {
            tts = new Tts(false, null, null, 0, null);
        }
        if (stt == null) {
            stt = new Stt(false, null, null, null, 0);
        }
    }

    public record Tts(boolean enabled, String languageCode, String voiceName,
                      double speakingRate, String credentialsPath) {
        public Tts {
            if (languageCode == null || languageCode.isBlank()) languageCode = "ko-KR";
            if (voiceName == null || voiceName.isBlank()) voiceName = "ko-KR-Neural2-A";
            if (speakingRate <= 0) speakingRate = 1.0;
            // credentialsPath가 null이면 ADC(GOOGLE_APPLICATION_CREDENTIALS 환경변수)로 폴백
        }
    }

    /**
     * 클라우드 STT(Groq Whisper). enabled=false면 비활성 → 브라우저 Web Speech 단독.
     * 활성 시 1차 클라우드 전사, 실패/빈결과면 브라우저 인식 텍스트로 폴백한다.
     */
    public record Stt(boolean enabled, String model, String language,
                      String baseUrl, int timeoutMs) {
        public Stt {
            if (model == null || model.isBlank()) model = "whisper-large-v3-turbo";
            if (language == null || language.isBlank()) language = "ko";
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.groq.com/openai/v1";
            if (timeoutMs <= 0) timeoutMs = 15000;
        }
    }
}
