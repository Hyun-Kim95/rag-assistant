package com.example.ragassistant.exception;

/**
 * Service에서 던지고, GlobalExceptionHandler가 HTTP 400으로 바꿈
 */
public class EmptyFileException extends RuntimeException{
    public EmptyFileException() {
        super("업로드된 파일이 비어 있습니다.");
    }
}
