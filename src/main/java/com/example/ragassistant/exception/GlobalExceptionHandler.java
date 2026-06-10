package com.example.ragassistant.exception;

import com.example.ragassistant.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmptyFileException.class)
    public ResponseEntity<ErrorResponse> handleEmptyFile(EmptyFileException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("EMPTY_FILE", ex.getMessage()));
    }
    @ExceptionHandler(UnsupportedDocumentFormatException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFormat(UnsupportedDocumentFormatException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("UNSUPPORTED_FORMAT", ex.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    // --- Ollama ---
    @ExceptionHandler(OllamaUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleOllamaUnavailable(OllamaUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("OLLAMA_UNAVAILABLE", ex.getMessage()));
    }
    @ExceptionHandler(OllamaResponseException.class)
    public ResponseEntity<ErrorResponse> handleOllamaResponse(OllamaResponseException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("OLLAMA_RESPONSE_ERROR", ex.getMessage()));
    }
    // --- DB ---
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabase(DataAccessException ex) {
        // 로그에는 ex 전체를 남기고, 클라이언트에는 짧은 메시지만
        log.warn("Database access failed", ex);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("DATABASE_UNAVAILABLE", "데이터베이스에 연결할 수 없습니다."));
    }
    // --- Document ---
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(DocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("DOCUMENT_NOT_FOUND", ex.getMessage()));
    }
    @ExceptionHandler(DuplicateDocumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateDocumentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("DUPLICATE_DOCUMENT", ex.getMessage()));
    }
    @ExceptionHandler(DocumentParseException.class)
    public ResponseEntity<ErrorResponse> handleDocumentParse(DocumentParseException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("DOCUMENT_PARSE_FAILED", ex.getMessage()));
    }
}
