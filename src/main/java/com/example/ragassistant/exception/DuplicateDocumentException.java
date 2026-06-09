package com.example.ragassistant.exception;

/**
 * 동일 파일명 문서가 이미 DB에 있을 때 upload()에서 던짐.
 * GlobalExceptionHandler → HTTP 409.
 */
public class DuplicateDocumentException extends RuntimeException {
    public DuplicateDocumentException(String filename) {
        super("이미 동일한 파일명의 문서가 존재합니다: " + filename);
    }
}
