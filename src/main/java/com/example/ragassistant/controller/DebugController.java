package com.example.ragassistant.controller;

import com.example.ragassistant.service.OllamaService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug/ollama")
public class DebugController {

    private final OllamaService ollamaService;

    public DebugController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @GetMapping("/chat")
    public Map<String, String> chat(@RequestParam(defaultValue = "Hello") String prompt) {
        return Map.of("response", ollamaService.chat(prompt));
    }

    @GetMapping("/embed")
    public Map<String, Object> embed(@RequestParam(defaultValue = "hello world") String text) {
        List<Double> embedding = ollamaService.embed(text);
        return Map.of(
                "dimensions", embedding.size(),
                "preview", embedding.subList(0, Math.min(5, embedding.size()))
        );
    }
}
