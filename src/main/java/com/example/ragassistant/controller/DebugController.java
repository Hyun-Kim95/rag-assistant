package com.example.ragassistant.controller;

import com.example.ragassistant.dto.ChunkResponse;
import com.example.ragassistant.service.ChunkService;
import com.example.ragassistant.service.OllamaService;

import java.util.List;
import java.util.Map;

import com.example.ragassistant.service.Retriever;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Debug", description = "개발용 디버그 API (운영 비노출 권장)")
@Profile("local")
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final OllamaService ollamaService;
    private final ChunkService chunkService;
    private final Retriever retriever;

    public DebugController(OllamaService ollamaService, ChunkService chunkService, Retriever retriever) {
        this.ollamaService = ollamaService;
        this.chunkService = chunkService;
        this.retriever = retriever;
    }

    @GetMapping("/ollama/chat")
    public Map<String, String> chat(@RequestParam(defaultValue = "Hello") String prompt) {
        return Map.of("response", ollamaService.chat(prompt));
    }

    @GetMapping("/ollama/embed")
    public Map<String, Object> embed(@RequestParam(defaultValue = "hello world") String text) {
        List<Double> embedding = ollamaService.embed(text);
        return Map.of(
                "dimensions", embedding.size(),
                "preview", embedding.subList(0, Math.min(5, embedding.size()))
        );
    }

    @GetMapping("/documents/{id}/chunks")
    public Map<String, Object> chunks(@PathVariable Long id) {
        List<ChunkResponse> chunks = chunkService.chunkByDocumentId(id).stream()
                .map(ChunkResponse::from)
                .toList();
        return Map.of(
                "documentId", id,
                "chunkCount", chunks.size(),
                "chunks", chunks
        );
    }

    // local 비교 API
    @GetMapping("/retrieval/compare")
    public Map<String, Object> compareRetrieval(@RequestParam String q) {
        return Map.of(
                "question", q,
                "vectorOnly", retriever.retrieveVectorOnlyForDebug(q),
                "hybrid", retriever.retrieveHybridForDebug(q)
        );
    }
}
