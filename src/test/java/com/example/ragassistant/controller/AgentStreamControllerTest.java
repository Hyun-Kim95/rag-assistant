package com.example.ragassistant.controller;

import com.example.ragassistant.agent.AgentOrchestrator;
import com.example.ragassistant.config.AgentProperties;
import com.example.ragassistant.dto.AgentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * AgentStreamController 수용 기준 검증.
 * AC-13: 빈 message는 SSE 스트림을 열기 전(=executor 실행 전) 400으로 차단되어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class AgentStreamControllerTest {

    @Mock
    AgentOrchestrator orchestrator;
    @Mock
    ExecutorService executor;

    @Test
    @DisplayName("스트리밍 빈 message → 스트림 열기 전 IllegalArgumentException(400)")
    void streamEmptyMessage_throwsBeforeStream() {
        AgentProperties props = new AgentProperties(5, 10, 180_000L, List.of("groq"), 6, 6, 4000, 8000);
        AgentStreamController controller =
                new AgentStreamController(orchestrator, props, new ObjectMapper(), executor);

        assertThatThrownBy(() -> controller.stream(new AgentRequest("   ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        // 스트림을 열지 않았으므로 오케스트레이터·실행자 모두 미호출
        verifyNoInteractions(orchestrator, executor);
    }
}
