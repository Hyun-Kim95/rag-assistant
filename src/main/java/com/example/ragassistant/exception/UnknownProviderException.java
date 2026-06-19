package com.example.ragassistant.exception;

/**
 * 요청이 지정한 provider 이름이 등록된 leg에 없을 때.
 * GlobalExceptionHandler → HTTP 400 (오타·잘못된 leg 이름 즉시 노출).
 */
public class UnknownProviderException extends RuntimeException {

    public UnknownProviderException(String message) {
        super(message);
    }
}
