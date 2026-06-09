package com.example.ragassistant.exception;

import com.example.ragassistant.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
