package com.example.ragassistant.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서비스 상태 확인")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Operation(summary = "헬스체크", description = "앱 기동 여부 확인")
    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "rag-assistant");
    }
}
