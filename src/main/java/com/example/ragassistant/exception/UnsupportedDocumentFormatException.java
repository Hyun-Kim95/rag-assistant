package com.example.ragassistant.exception;

/**
 * Service에서 던지고, GlobalExceptionHandler가 HTTP 400으로 바꿈
 */
public class UnsupportedDocumentFormatException extends RuntimeException{
    public UnsupportedDocumentFormatException(String filename) {
        super("지원하지 않는 파일 형식입니다: " + filename);
    }
}
