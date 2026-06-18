package com.example.ragassistant.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.ragassistant.service.HealthService;
import com.example.ragassistant.service.HealthService.HealthReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서비스 상태 확인")
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(summary = "헬스체크", description = "앱 + 의존성(Ollama·DB·TEI reranker) 상태. DOWN이면 503")
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        HealthReport report = healthService.check();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", report.status());
        body.put("service", "rag-assistant");
        body.put("dependencies", report.dependencies());

        HttpStatus httpStatus = "DOWN".equals(report.status())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(body);
    }
}
