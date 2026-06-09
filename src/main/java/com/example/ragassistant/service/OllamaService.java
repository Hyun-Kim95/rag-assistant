package com.example.ragassistant.service;

import com.example.ragassistant.config.OllamaProperties;
import java.util.List;
import java.util.Map;

import com.example.ragassistant.exception.OllamaResponseException;
import com.example.ragassistant.exception.OllamaUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class OllamaService {

    private final RestClient ollamaRestClient;
    private final OllamaProperties properties;

    public OllamaService(RestClient ollamaRestClient, OllamaProperties properties) {
        this.ollamaRestClient = ollamaRestClient;
        this.properties = properties;
    }

    public String chat(String prompt) {
        Map<String, Object> request = Map.of(
                "model", properties.chatModel(),
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "당신은 한국어로만 답하는 문서 Q&A 어시스턴트입니다. "
                                        + "중국어와 영어로 답하지 마세요."),
                        Map.of("role", "user", "content", prompt)),
                "stream", false
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
}
