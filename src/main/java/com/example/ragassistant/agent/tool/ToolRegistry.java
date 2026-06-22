package com.example.ragassistant.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.ragassistant.llm.agent.ToolSpec;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 등록된 모든 AgentTool을 모아 스펙 노출·이름 디스패치한다.
 * 새 도구는 AgentTool 구현 + @Component 만 추가하면 자동 등록(확장 포인트).
 */
@Service
public class ToolRegistry {

    private final Map<String, AgentTool> byName;

    public ToolRegistry(List<AgentTool> tools) {
        Map<String, AgentTool> map = new LinkedHashMap<>();
        for (AgentTool t : tools) {
            AgentTool prev = map.put(t.name(), t);
            if (prev != null) {
                throw new IllegalStateException("도구 name 중복: " + t.name());
            }
        }
        this.byName = map;
    }

    /**
     * 모델에 노출할 도구 스펙 목록
     */
    public List<ToolSpec> specs() {
        return byName.values().stream()
                .map(t -> new ToolSpec(t.name(), t.description(), t.parametersSchema()))
                .toList();
    }

    /**
     * 이름으로 도구 실행. 없는 도구면 모델이 복구하도록 오류 텍스트 반환.
     */
    public ToolResult execute(String name, JsonNode arguments) {
        AgentTool tool = byName.get(name);
        if (tool == null) {
            return ToolResult.text("알 수 없는 도구: " + name);
        }
        return tool.execute(arguments);
    }
}
