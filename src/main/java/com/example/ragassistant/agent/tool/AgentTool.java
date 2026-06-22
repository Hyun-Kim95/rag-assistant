package com.example.ragassistant.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * agent가 호출할 수 있는 도구 1개.
 * - name/description/parametersSchema: 모델에 노출할 function 스펙.
 * - execute: 모델이 준 인자(JSON)로 실행 -> ToolResult.
 * 구현체는 인자 검증 실패·도메인 오류를 예외 대신 ToolResult.text(오류 설명)로 돌려
 * 모델이 스스로 복구(재호출)할 수 있게 한다.
 */
public interface AgentTool {

    String name();

    String description();

    Map<String, Object> parametersSchema();

    ToolResult execute(JsonNode arguments);
}
