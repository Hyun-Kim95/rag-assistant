package com.example.ragassistant.exception;

/**
 * 지원 확장자 파일이지만 본문 추출에 실패했을 때 사용.
 * 예: 손상된 PDF, 암호 PDF, PDFBox IOException
 * GlobalExceptionHandler가 HTTP 400 + code DOCUMENT_PARSE_FAILED 로 변환.
 */
public class DocumentParseException extends RuntimeException {

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
