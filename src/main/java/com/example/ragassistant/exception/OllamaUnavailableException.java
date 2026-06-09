package com.example.ragassistant.exception;

/**
 * Ollama 프로세스 미실행, 연결 거부, 타임아웃 등 "서비스에 닿을 수 없을 때".
 * GlobalExceptionHandler → HTTP 503.
 * Spring RestClient는 연결 실패 시 ResourceAccessException을 던진다.
 */
public class OllamaUnavailableException extends RuntimeException {

    public OllamaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
