package com.example.ragassistant.dto;

import java.util.List;

/**
 * 클라이언트에 보낼 json형태(본문 없음)
 * @param documents
 */
public record DocumentListResponse(List<DocumentResponse> documents) {

}
