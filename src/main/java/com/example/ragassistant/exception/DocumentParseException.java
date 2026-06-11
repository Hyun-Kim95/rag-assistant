package com.example.ragassistant.exception;

/**
 * 지원 확장자 파일이지만 본문 추출에 실패했을 때 사용.
 * 예:
 * - 손상된 PDF (PDFBox IOException)
 * - 암호 PDF (InvalidPasswordException)
 * - 텍스트 레이어 없는 PDF (스캔본 — OCR 미지원)
 * GlobalExceptionHandler → HTTP 400, error=DOCUMENT_PARSE_FAILED
 */
public class DocumentParseException extends RuntimeException {

    // 사용자-facing 메시지만 전달 (cause 없음)
    public DocumentParseException(String message) {
        super(message);
    }

    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
