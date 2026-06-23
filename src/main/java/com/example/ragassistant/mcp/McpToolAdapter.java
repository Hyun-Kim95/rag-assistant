package com.example.ragassistant.mcp;

import com.example.ragassistant.agent.tool.ToolRegistry;
import com.example.ragassistant.agent.tool.ToolResult;
import com.example.ragassistant.dto.SourceCitation;
import com.example.ragassistant.llm.agent.ToolSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 기존 ToolRegistry <-> MCP 형식 변환.
 * - listTools(): ToolSpec(name, description, parameters=JSON Schema) -> MCP {name, description, inputSchema}
 * - callTool(): ToolResult.content + sources -> MCP {content:[{type:text,text}], isError:false}
 * 없는 도구 판정은 호출자(McpMethodHandler)가 hasTool()로 선행한다(ToolRegistry.execute는
 * 없는 도구도 예외 대신 텍스트를 돌려주므로 문자열 매칭에 의존하지 않기 위함).
 */
@Component
@Profile("mcp-stdio")
public class McpToolAdapter {

    private final ToolRegistry toolRegistry;

    public McpToolAdapter(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolSpec spec : toolRegistry.specs()) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", spec.name());
            tool.put("description", spec.description());
            tool.put("inputSchema", spec.parameters());
            tools.add(tool);
        }
        return tools;
    }

    public boolean hasTool(String name) {
        return toolRegistry.specs().stream().anyMatch(s -> s.name().equals(name));
    }

    public Map<String, Object> callTool(String name, JsonNode arguments) {
        JsonNode args = (arguments == null || arguments.isNull() || arguments.isMissingNode())
                ? JsonNodeFactory.instance.objectNode()
                : arguments;
        ToolResult result = toolRegistry.execute(name, args);
        String text = result.content();
        if (result.sources() != null && !result.sources().isEmpty()) {
            text = text + "\n\n" + formatSources(result.sources());
        }
        Map<String, Object> contentItem = new LinkedHashMap<>();
        contentItem.put("type", "text");
        contentItem.put("text", text);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", List.of(contentItem));
        out.put("isError", false);
        return out;
    }

    private static String formatSources(List<SourceCitation> sources) {
        StringBuilder sb = new StringBuilder("[출처]\n");
        for (int i = 0; i < sources.size(); i++) {
            SourceCitation s = sources.get(i);
            sb.append(i + 1).append(". ").append(s.documentName())
                    .append(" (chunkId=").append(s.chunkId())
                    .append(", score=").append(String.format(Locale.ROOT, "%.3f", s.score()))
                    .append(")\n");
        }
        return sb.toString().trim();
    }
}
