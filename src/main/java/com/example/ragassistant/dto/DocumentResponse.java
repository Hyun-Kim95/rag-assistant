package com.example.ragassistant.dto;

import java.time.LocalDateTime;

/**
 * 클라이언트에 보낼 json형태(본문 없음)
 * @param id
 * @param name
 * @param contentType
 * @param textLength
 * @param createdAt
 */
public record DocumentResponse(
        Long id,
        String name,
        String contentType,
        int textLength,
        LocalDateTime createdAt
) {

}
