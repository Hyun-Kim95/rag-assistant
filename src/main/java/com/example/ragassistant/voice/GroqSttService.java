package com.example.ragassistant.voice;

import com.example.ragassistant.config.OpenAiCompatProperties;
import com.example.ragassistant.config.VoiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Groq Whisper 클라우드 STT(OpenAI 호환 /audio/transcriptions).
 * 오디오 바이트 → 전사 텍스트. 비활성/실패/빈결과 시 null을 반환해 호출자가 브라우저 STT로 폴백한다.
 *
 * <p>api-key는 LLM과 같은 Groq 키(llm.openai-compat.api-key)를 재사용한다(중복 키 없음).
 * 무료 티어 한도(20 RPM / 2000 RPD)를 넘기면 429 → 예외 흡수 → null(폴백).
 */
@Service
public class GroqSttService {

    private static final Logger log = LoggerFactory.getLogger(GroqSttService.class);

    private final RestClient client;
    private final VoiceProperties.Stt props;
    private final String apiKey;

    public GroqSttService(@Qualifier("sttRestClient") RestClient sttRestClient,
                          VoiceProperties voiceProperties, OpenAiCompatProperties openAiCompatProperties) {
        this.client = sttRestClient;
        this.props = voiceProperties.stt();
        this.apiKey = openAiCompatProperties.apiKey();
    }

    /**
     * 활성(enabled) + Groq 키가 있어야 라우팅 후보. 실제 도달성 핑은 아님.
     */
    public boolean available() {
        return props.enabled() && StringUtils.hasText(apiKey);
    }

    /**
     * 오디오 → 전사 텍스트. 비활성/빈입력/실패/빈결과 시 null(→ 브라우저 폴백).
     *
     * @param audio    녹음된 오디오 바이트(브라우저 MediaRecorder webm/opus)
     * @param filename multipart 파일명(확장자로 포맷 추론 — 예: audio.webm)
     */
    public String transcribe(byte[] audio, String filename) {
        if (!available() || audio == null || audio.length == 0) {
            return null;
        }
        try {
            String boundary = "----ragstt" + System.nanoTime();
            byte[] multipart = buildMultipart(boundary,
                    StringUtils.hasText(filename) ? filename : "audio.webm", audio);

            String text = client.post()
                    .uri("/audio/transcriptions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                    .body(multipart)
                    .retrieve()
                    .body(String.class);

            return StringUtils.hasText(text) ? text.trim() : null;
        } catch (Exception e) {
            // 429(rate limit)·네트워크·5xx 등 모두 흡수 → 브라우저 STT 폴백. 통화는 끊기지 않는다.
            log.warn("Groq STT 전사 실패 → 브라우저 폴백: {}", e.getMessage());
            return null;
        }
    }

    /**
     * multipart/form-data 본문을 직접 바이트로 구성한다.
     * (RestClient의 MultipartBodyBuilder 경로는 reactive-streams 런타임 의존성을 요구하므로 회피)
     */
    private byte[] buildMultipart(String boundary, String filename, byte[] audio) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeField(out, boundary, "model", props.model());
        writeField(out, boundary, "language", props.language());
        writeField(out, boundary, "response_format", "text");
        // 파일 파트
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(audio);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
