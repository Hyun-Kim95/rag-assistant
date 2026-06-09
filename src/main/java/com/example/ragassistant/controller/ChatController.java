package com.example.ragassistant.controller;

import com.example.ragassistant.dto.ChatRequest;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "RAG 기반 질의응답")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @Operation(summary = "RAG 채팅", description = "업로드된 문서를 검색해 근거 기반 답변 + 출처 반환")
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return ragService.chat(request.question());
    }
}
