package com.example.ragassistant.exception;

/**
 * chat provider가 응답했으나 HTTP 오류/body 없음/형식 불일치일 때의 공통 상위 타입.
 * GlobalExceptionHandler → HTTP 502.
 * Ollama 전용 OllamaResponseException 은 이 타입을 상속한다.
 */
public class LlmResponseException extends RuntimeException {

    public LlmResponseException(String message) {
        super(message);
    }

    public LlmResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}