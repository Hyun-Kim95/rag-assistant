package com.example.ragassistant.dto;

/**
 * 클라이언트에 보낼 json형태(본문 없음)
 * @param error
 * @param message
 */
public record ErrorResponse(String error, String message) {
}
