package com.example.ragassistant.voice;

import com.example.ragassistant.config.VoiceProperties;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;

/**
 * Google Cloud TTS(Neural2) 합성. 자격증명은 GOOGLE_APPLICATION_CREDENTIALS 환경변수.
 * 비활성(enabled=false)·init 실패·합성 실패 시 null 반환 → 호출자가 브라우저 폴백.
 */
@Service
public class GoogleTtsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTtsService.class);

    private final VoiceProperties props;
    private volatile TextToSpeechClient client; // null이면 비활성/폴백

    public GoogleTtsService(VoiceProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        if (!props.tts().enabled()) {
            log.info("Google TTS disabled (voice.tts.enabled=false) → 브라우저 폴백");
            return;
        }
        try {
            String credPath = props.tts().credentialsPath();
            if (credPath != null && !credPath.isBlank()) {
                // 파일 경로에서 직접 자격증명 로드 (환경변수 불필요)
                try (FileInputStream in = new FileInputStream(credPath)) {
                    GoogleCredentials creds = GoogleCredentials.fromStream(in);
                    TextToSpeechSettings settings = TextToSpeechSettings.newBuilder()
                            .setCredentialsProvider(FixedCredentialsProvider.create(creds))
                            .build();
                    this.client = TextToSpeechClient.create(settings);
                }
            } else {
                // 경로 미지정 → ADC(GOOGLE_APPLICATION_CREDENTIALS) 사용
                this.client = TextToSpeechClient.create();
            }
            log.info("Google TTS enabled: voice={}, lang={}",
                    props.tts().voiceName(), props.tts().languageCode());
        } catch (Exception e) {
            log.warn("Google TTS init 실패 → 브라우저 폴백: {}", e.getMessage());
            this.client = null;
        }
    }

    /**
     * 텍스트 → MP3 바이트. 비활성/실패 시 null.
     */
    public byte[] synthesize(String text) {
        if (client == null) {
            return null;
        }
        String clean = SpeechTextSanitizer.clean(text);
        if (clean.isBlank()) {
            return null;
        }
        try {
            SynthesisInput input = SynthesisInput.newBuilder().setText(clean).build();
            VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode(props.tts().languageCode())
                    .setName(props.tts().voiceName())
                    .build();
            AudioConfig audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .setSpeakingRate(props.tts().speakingRate())
                    .build();
            SynthesizeSpeechResponse resp = client.synthesizeSpeech(input, voice, audioConfig);
            return resp.getAudioContent().toByteArray();
        } catch (Exception e) {
            log.warn("Google TTS 합성 실패 → 폴백: {}", e.getMessage());
            return null;
        }
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }
}
