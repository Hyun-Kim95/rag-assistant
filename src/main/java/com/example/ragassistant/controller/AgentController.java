package com.example.ragassistant.controller;

import com.example.ragassistant.agent.AgentOrchestrator;
import com.example.ragassistant.dto.AgentRequest;
import com.example.ragassistant.dto.AgentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent", description = "tool calling 기반 에이전트")
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Operation(summary = "에이전트 질의", description = "필요한 도구(문서 검색 등)를 호출해 멀티스텝으로 답한다. messages로 이전 대화를 보내면 멀티턴.")
    @PostMapping
    public AgentResponse agent(@RequestBody AgentRequest request) {
        return orchestrator.run(request.message(), request.provider(), request.messages());
    }
}
