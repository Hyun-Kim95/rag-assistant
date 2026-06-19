package com.example.ragassistant.service;

import com.example.ragassistant.config.OllamaProperties;

import java.util.List;
import java.util.Map;

import com.example.ragassistant.exception.OllamaResponseException;
import com.example.ragassistant.exception.OllamaUnavailableException;
import com.example.ragassistant.llm.EmbeddingModelClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Ollama 임베딩 전용 클라이언트. (chat leg는 OllamaChatClient로 분리됨)
 */
@Service
public class OllamaService implements EmbeddingModelClient {

    private final RestClient ollamaRestClient;
    private final OllamaProperties properties;

    public OllamaService(RestClient ollamaRestClient, OllamaProperties properties) {
        this.ollamaRestClient = ollamaRestClient;
        this.properties = properties;
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
            throw new OllamaUnavailableException(
                    "Ollama에 연결할 수 없습니다. Docker/서비스 실행 및 base-url("
                            + properties.baseUrl() + ")을 확인하세요.",
                    ex
            );
        } catch (HttpStatusCodeException ex) {
            throw new OllamaResponseException(
                    "Ollama HTTP 오류: " + ex.getStatusCode().value() + " uri=" + uri,
                    ex
            );
        }
    }
}
