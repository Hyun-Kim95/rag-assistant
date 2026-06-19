package com.example.ragassistant.service;

import com.example.ragassistant.config.OllamaProperties;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.example.ragassistant.exception.OllamaResponseException;
import com.example.ragassistant.exception.OllamaUnavailableException;
import com.example.ragassistant.llm.ChatModelClient;
import com.example.ragassistant.llm.ChatPrompts;
import com.example.ragassistant.llm.EmbeddingModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class OllamaService implements ChatModelClient, EmbeddingModelClient {

    private final RestClient ollamaRestClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;

    public OllamaService(RestClient ollamaRestClient, OllamaProperties properties, ObjectMapper objectMapper) {
        this.ollamaRestClient = ollamaRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "ollama-7b";
    }

    @Override
    public String chat(String prompt) {
        Map<String, Object> request = Map.of(
                "model", properties.chatModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", ChatPrompts.SYSTEM),
                        Map.of("role", "user", "content", prompt)),
                "stream", false,
                "options", Map.of("temperature", properties.temperature())
        );

        Map<?, ?> response = postForMap("/api/chat", request);

        if (response.get("message") == null) {
            throw new OllamaResponseException("Ollama chat 응답에 message 필드가 없습니다.");
        }

        Object message = response.get("message");
        if (message instanceof Map<?, ?> messageMap && messageMap.get("content") != null) {
            return messageMap.get("content").toString();
        }

        throw new OllamaResponseException("Ollama chat 응답 형식이 예상과 다릅니다.");
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Double> embed(String text) {
        Map<String, Object> request = Map.of(
                "model", properties.embeddingModel(),
                "input", text
        );

        Map<String, Object> response = postForMap("/api/embed", request);

        if (response.get("embeddings") == null) {
            throw new OllamaResponseException("Ollama embed 응답에 embeddings 필드가 없습니다.");
        }

        List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
        if (embeddings.isEmpty()) {
            throw new OllamaResponseException("Ollama embed 결과가 비어 있습니다.");
        }

        return embeddings.get(0);
    }

    /**
     * Ollama POST 공통: 연결 실패(503) vs HTTP/파싱 실패(502)를 한곳에서 분류.
     */
    @SuppressWarnings("unchecked")
    private <T> T postForMap(String uri, Map<String, Object> body) {
        try {
            T result = (T) ollamaRestClient.post()
                    .uri(uri)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (result == null) {
                throw new OllamaResponseException("Ollama 응답 body가 null입니다. uri=" + uri);
            }
            return result;
        } catch (ResourceAccessException ex) {
            // ConnectException, SocketTimeoutException 등 포함
            throw new OllamaUnavailableException(
                    "Ollama에 연결할 수 없습니다. Docker/서비스 실행 및 base-url("
                            + properties.baseUrl() + ")을 확인하세요.",
                    ex
            );
        } catch (HttpStatusCodeException ex) {
            // Ollama가 4xx/5xx HTTP를 반환한 경우 (모델 없음 등)
            throw new OllamaResponseException(
                    "Ollama HTTP 오류: " + ex.getStatusCode().value() + " uri=" + uri,
                    ex
            );
        }
    }

    private Map<String, Object> buildChatRequest(String prompt, boolean stream) {
        return Map.of(
                "model", properties.chatModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", ChatPrompts.SYSTEM),
                        Map.of("role", "user", "content", prompt)),
                "stream", stream,
                "options", Map.of("temperature", properties.temperature())
        );
    }

    /**
     * Ollama /api/chat stream=true — NDJSON 한 줄씩 파싱.
     *
     * @param onDelta 토큰(또는 content 조각)마다 호출
     * @return 전체 assistant 답변 (`RagService.isGrounded` 판별용)
     */
    @Override
    public String streamChat(String prompt, Consumer<String> onDelta) {
        Map<String, Object> request = buildChatRequest(prompt, true);

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
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }
                                JsonNode root = objectMapper.readTree(line);
                                JsonNode contentNode = root.path("message").path("content");
                                if (contentNode.isMissingNode() || contentNode.isNull()) {
                                    continue;
                                }
                                String piece = contentNode.asText();
                                if (piece.isEmpty()) {
                                    continue;
                                }
                                full.append(piece);
                                onDelta.accept(piece);
                            }
                        }
                        return full.toString();
                    });
        } catch (ResourceAccessException ex) {
            throw new OllamaUnavailableException(
                    "Ollama에 연결할 수 없습니다. Docker/서비스 실행 및 base-url("
                            + properties.baseUrl() + ")을 확인하세요.",
                    ex);
        } catch (OllamaResponseException | OllamaUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OllamaResponseException("Ollama stream 처리 중 오류", ex);
        }
    }
}
