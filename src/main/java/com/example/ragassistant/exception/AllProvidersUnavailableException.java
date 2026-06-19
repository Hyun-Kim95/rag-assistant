package com.example.ragassistant.exception;

/**
 * 라우터가 체인의 모든 chat provider를 시도했지만 전부 실패했을 때.
 * GlobalExceptionHandler → HTTP 503.
 */
public class AllProvidersUnavailableException extends RuntimeException {

    public AllProvidersUnavailableException(String message) {
        super(message);
    }
}
