package com.example.ragassistant.exception;

/**
 * chat provider에 닿을 수 없을 때(연결 거부·타임아웃 등)의 공통 상위 타입.
 * GlobalExceptionHandler → HTTP 503.
 * RoutingChatModelClient 는 이 타입을 catch 해 다음 leg로 폴백한다.
 * Ollama 전용 OllamaUnavailableException 은 이 타입을 상속한다.
 */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
