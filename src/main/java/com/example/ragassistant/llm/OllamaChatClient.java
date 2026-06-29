package com.example.ragassistant.llm;

import com.example.ragassistant.exception.OllamaResponseException;
import com.example.ragassistant.exception.OllamaUnavailableException;
import com.example.ragassistant.observability.QueryTelemetryContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 단일 Ollama chat 모델 leg. model·name 을 주입받아 여러 모델(7b·1b)을 동일 코드로 운영한다.
 * - chat/streamChat: RAG 시스템 프롬프트(ChatPrompts.SYSTEM) 사용.
 * - complete(system,user): 임의 시스템 프롬프트로 1회 동기 호출(난이도 분류 등 내부용).
 * 예외는 OllamaUnavailableException(연결, 503)·OllamaResponseException(응답, 502)로 분류 → 라우터 폴백.
 */
public class OllamaChatClient implements ChatModelClient {

    private final RestClient ollamaRestClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String name;
    private final double temperature;
    private final QueryTelemetryContext telemetry;

    public OllamaChatClient(RestClient ollamaRestClient, ObjectMapper objectMapper,
                            String model, String name, double temperature, QueryTelemetryContext telemetry) {
        this.ollamaRestClient = ollamaRestClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.name = name;
        this.temperature = temperature;
        this.telemetry = telemetry;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String chat(String prompt) {
        return complete(ChatPrompts.SYSTEM, prompt);
    }

    /**
     * 임의 시스템 프롬프트로 1회 동기 호출. 분류기 등 내부 용도.
     */
    public String complete(String system, String user) {
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "stream", false,
                "options", Map.of("temperature", temperature, "num_predict", 1024, "num_ctx", 4096));
        Map<?, ?> response = postForMap("/api/chat", request);
        if (response.get("message") == null) {
            throw new OllamaResponseException("Ollama chat 응답에 message 필드가 없습니다.");
        }
        if (response.get("message") instanceof Map<?, ?> messageMap && messageMap.get("content") != null) {
            recordTokens(asInt(response.get("prompt_eval_count")), asInt(response.get("eval_count")));
            return messageMap.get("content").toString();
        }
        throw new OllamaResponseException("Ollama chat 응답 형식이 예상과 다릅니다.");
    }

    @SuppressWarnings("unchecked")
    private <T> T postForMap(String uri, Map<String, Object> body) {
        try {
            T result = (T) ollamaRestClient.post().uri(uri).body(body).retrieve().body(Map.class);
            if (result == null) {
                throw new OllamaResponseException("Ollama 응답 body가 null입니다. uri=" + uri);
            }
            return result;
        } catch (ResourceAccessException ex) {
            // 연결 거부·read timeout 등 I/O 단계 실패
            throw new OllamaUnavailableException("Ollama에 연결할 수 없습니다(연결/타임아웃). model=" + model, ex);
        } catch (HttpStatusCodeException ex) {
            throw new OllamaResponseException(
                    "Ollama HTTP 오류: " + ex.getStatusCode().value() + " uri=" + uri + " model=" + model, ex);
        } catch (RestClientException ex) {
            // 응답 추출 단계에서 발생한 timeout 등(헤더/본문 파싱 중 SocketTimeoutException)도 "닿을 수 없음"으로 분류 → 라우터 폴백 대상
            throw new OllamaUnavailableException("Ollama 응답 처리 실패(타임아웃/추출 오류). model=" + model, ex);
        }
    }

    @Override
    public String streamChat(String prompt, Consumer<String> onDelta) {
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", ChatPrompts.SYSTEM),
                        Map.of("role", "user", "content", prompt)),
                "stream", true,
                "options", Map.of("temperature", temperature, "num_predict", 1024, "num_ctx", 4096));
        try {
            return ollamaRestClient.post()
                    .uri("/api/chat")
                    .body(request)
                    .exchange((req, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw new OllamaResponseException(
                                    "Ollama HTTP 오류: " + response.getStatusCode().value() + " uri=/api/chat");
                        }
                        StringBuilder full = new StringBuilder();
                        Integer pTok = null;
                        Integer cTok = null;
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) continue;
                                JsonNode root = objectMapper.readTree(line);
                                if (root.path("done").asBoolean(false)) {
                                    if (root.has("prompt_eval_count")) pTok = root.get("prompt_eval_count").asInt();
                                    if (root.has("eval_count")) cTok = root.get("eval_count").asInt();
                                }
                                JsonNode contentNode = root.path("message").path("content");
                                if (contentNode.isMissingNode() || contentNode.isNull()) continue;
                                String piece = contentNode.asText();
                                if (piece.isEmpty()) continue;
                                full.append(piece);
                                onDelta.accept(piece);
                            }
                        }
                        recordTokens(pTok, cTok);
                        return full.toString();
                    });
        } catch (ResourceAccessException ex) {
            throw new OllamaUnavailableException("Ollama에 연결할 수 없습니다. model=" + model, ex);
        } catch (OllamaResponseException | OllamaUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OllamaResponseException("Ollama stream 처리 중 오류", ex);
        }
    }

    private void recordTokens(Integer prompt, Integer completion) {
        if (telemetry != null) {
            telemetry.recordTokens(prompt, completion);
        }
    }

    private static Integer asInt(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }
}
