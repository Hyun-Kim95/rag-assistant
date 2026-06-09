package com.example.ragassistant.exception;

/**
 * DELETE / GET 등에서 id에 해당하는 documents 행이 없을 때.
 * GlobalExceptionHandler → HTTP 404.
 */
public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(Long id) {
        super("문서를 찾을 수 없습니다: id=" + id);
    }
}
