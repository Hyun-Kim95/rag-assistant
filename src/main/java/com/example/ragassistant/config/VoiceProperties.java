package com.example.ragassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 음성(TTS) 설정. 서비스계정 키는 credentials-path 또는 GOOGLE_APPLICATION_CREDENTIALS.
 */
@ConfigurationProperties(prefix = "voice")
public record VoiceProperties(Tts tts) {

    public VoiceProperties {
        if (tts == null) {
            tts = new Tts(false, null, null, 0, null);
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
}
