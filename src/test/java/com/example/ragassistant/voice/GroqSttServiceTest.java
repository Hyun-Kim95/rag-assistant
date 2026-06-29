package com.example.ragassistant.voice;

import com.example.ragassistant.config.OpenAiCompatProperties;
import com.example.ragassistant.config.VoiceProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GroqSttService 단위 검증 (실제 Groq 호출 없이 JDK 내장 HttpServer로 결정적).
 * 정상 전사 / 실패(429) 폴백(null) / 비활성·빈입력 시 호출 없이 null.
 * (MockRestServiceServer는 RestClient 바인딩 시 reactive-streams를 요구하므로 경량 로컬 HTTP 스텁 사용.)
 */
class GroqSttServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private GroqSttService service(boolean enabled, String apiKey, String baseUrl) {
        VoiceProperties vp = new VoiceProperties(null,
                new VoiceProperties.Stt(enabled, "whisper-large-v3-turbo", "ko", baseUrl, 15000));
        OpenAiCompatProperties op = new OpenAiCompatProperties(true, "groq", baseUrl, apiKey, "llama-3.1-8b-instant", 8000);
        RestClient client = RestClient.builder().baseUrl(baseUrl).build();
        return new GroqSttService(client, vp, op);
    }

    /**
     * 지정 상태/본문으로 응답하는 스텁 서버를 띄우고 base-url을 반환. auth 헤더·요청 수를 캡처.
     */
    private String startStub(int status, String body,
                             AtomicReference<String> authOut, AtomicInteger countOut) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio/transcriptions", exchange -> {
            countOut.incrementAndGet();
            authOut.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            if (status >= 400) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    @DisplayName("정상 응답 → 전사 텍스트(trim) 반환 + Authorization 헤더 전달")
    void transcribeSuccess() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        String base = startStub(200, "  환불 정책 알려줘\n", auth, count);

        String text = service(true, "gsk_test", base).transcribe(new byte[]{1, 2, 3}, "audio.webm");

        assertThat(text).isEqualTo("환불 정책 알려줘");
        assertThat(auth.get()).isEqualTo("Bearer gsk_test");
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("429(rate limit) → 예외 흡수 후 null(브라우저 폴백)")
    void transcribeRateLimitedReturnsNull() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        String base = startStub(429, null, auth, count);

        assertThat(service(true, "gsk_test", base).transcribe(new byte[]{1, 2, 3}, "audio.webm")).isNull();
        assertThat(count.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("비활성(enabled=false) → HTTP 호출 없이 null")
    void disabledReturnsNullWithoutCall() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        String base = startStub(200, "응답", auth, count);

        GroqSttService svc = service(false, "gsk_test", base);

        assertThat(svc.transcribe(new byte[]{1, 2, 3}, "audio.webm")).isNull();
        assertThat(svc.available()).isFalse();
        assertThat(count.get()).isZero();   // 비활성이면 서버를 치지 않는다
    }

    @Test
    @DisplayName("키 없음/빈 오디오 → null (available=false, 빈입력 방어)")
    void missingKeyOrEmptyAudio() {
        String base = "http://127.0.0.1:1";   // 호출되지 않아야 하므로 도달 불가 주소
        assertThat(service(true, "", base).available()).isFalse();
        assertThat(service(true, "", base).transcribe(new byte[]{1}, "a.webm")).isNull();
        assertThat(service(true, "gsk_test", base).transcribe(new byte[]{}, "a.webm")).isNull();
    }
}
