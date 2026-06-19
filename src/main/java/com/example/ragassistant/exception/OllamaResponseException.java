package com.example.ragassistant.exception;

/**
 * Ollama는 응답했지만 body가 비었거나, JSON 구조가 기대와 다를 때.
 * GlobalExceptionHandler → HTTP 502.
 * "연결은 됐는데 결과가 깨짐"과 503을 구분해 디버깅·모니터링에 유리하다.
 */
public class OllamaResponseException extends LlmResponseException {

    public OllamaResponseException(String message) {
        super(message);
    }

    public OllamaResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
