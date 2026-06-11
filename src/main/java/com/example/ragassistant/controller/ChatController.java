package com.example.ragassistant.controller;

import com.example.ragassistant.dto.ChatRequest;
import com.example.ragassistant.dto.ChatResponse;
import com.example.ragassistant.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @Operation(summary = "RAG 채팅 (스트리밍)", description = "SSE: delta → done(sources, grounded)")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        // 빈 질문은 스트림 열기 전에 검증 → GlobalExceptionHandler 400 JSON
        if (request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("질문이 비어 있습니다.");
        }
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5분
        new Thread(() -> ragService.chatStream(request.question(), emitter)).start();
        return emitter;
    }
}
