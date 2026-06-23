package com.example.ragassistant.mcp;

import com.example.ragassistant.agent.tool.AgentTool;
import com.example.ragassistant.agent.tool.ToolRegistry;
import com.example.ragassistant.agent.tool.ToolResult;
import com.example.ragassistant.dto.SourceCitation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * McpToolAdapter: ToolRegistry -> MCP 형식 변환 검증(DB/Ollama 불필요, fake 도구 사용).
 * tools/list 매핑, tools/call 정상+출처, 없는 도구 hasTool=false.
 */
class McpToolAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // 실제 도구(DB/Ollama 의존) 대신 고정 스키마·결과를 돌려주는 가짜 AgentTool.
    // ToolRegistry에 끼워 어댑터 변환만 순수하게 검증한다.
    private static AgentTool fakeTool(String name, Map<String, Object> schema, ToolResult result) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name + " 설명";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return schema;
            }

            @Override
            public ToolResult execute(JsonNode arguments) {
                return result;
            }
        };
    }

    // ToolSpec(name/description/parameters)이 MCP tools 항목
    // {name, description, inputSchema}로 1:1 매핑되는지. 특히 parametersSchema가
    // 가공 없이 inputSchema로 그대로 전달되는지 확인.
    @Test
    void listTools_mapsSpecsToNameDescriptionInputSchema() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query"));
        ToolRegistry registry = new ToolRegistry(List.of(
                fakeTool("search_documents", schema, ToolResult.text("ok"))));
        McpToolAdapter adapter = new McpToolAdapter(registry);

        List<Map<String, Object>> tools = adapter.listTools();

        assertThat(tools).hasSize(1);
        Map<String, Object> tool = tools.get(0);
        assertThat(tool.get("name")).isEqualTo("search_documents");
        assertThat(tool.get("description")).isEqualTo("search_documents 설명");
        // inputSchema는 원본 parametersSchema와 동일 객체여야 한다(변형 금지).
        assertThat(tool.get("inputSchema")).isEqualTo(schema);
    }

    // ToolResult.content가 MCP {content:[{type:"text", text}], isError:false}로
    // 감싸지는지. 출처 없는 도구는 텍스트만 그대로 실린다.
    @Test
    void callTool_returnsTextContentAndIsErrorFalse() {
        ToolRegistry registry = new ToolRegistry(List.of(
                fakeTool("list_documents", Map.of("type", "object"), ToolResult.text("문서 2건"))));
        McpToolAdapter adapter = new McpToolAdapter(registry);

        Map<String, Object> result = adapter.callTool("list_documents", mapper.createObjectNode());

        assertThat(result.get("isError")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("type")).isEqualTo("text");
        assertThat(content.get(0).get("text")).isEqualTo("문서 2건");
    }

    // ToolResult.sources가 있으면 content 텍스트 끝에 [출처] 블록으로
    // append되는지(문서명·chunkId 포함). MCP에는 별도 sources 필드가 없으므로 텍스트로 전달.
    @Test
    void callTool_appendsSourcesToContentText() {
        ToolResult withSources = new ToolResult(
                "검색 결과 1건",
                List.of(new SourceCitation("guide.md", 7L, "snippet", 0.912)));
        ToolRegistry registry = new ToolRegistry(List.of(
                fakeTool("search_documents", Map.of("type", "object"), withSources)));
        McpToolAdapter adapter = new McpToolAdapter(registry);

        Map<String, Object> result = adapter.callTool("search_documents", mapper.createObjectNode());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertThat(text).contains("검색 결과 1건");
        assertThat(text).contains("[출처]");
        assertThat(text).contains("guide.md");
        assertThat(text).contains("chunkId=7");
    }

    // 없는 도구 판정은 호출자가 hasTool로 선행한다. 등록된 이름만 true,
    // 미등록 이름은 false여야 핸들러가 -32602로 분기할 수 있다.
    @Test
    void hasTool_trueOnlyForRegisteredTool() {
        ToolRegistry registry = new ToolRegistry(List.of(
                fakeTool("search_documents", Map.of("type", "object"), ToolResult.text("ok"))));
        McpToolAdapter adapter = new McpToolAdapter(registry);

        assertThat(adapter.hasTool("search_documents")).isTrue();
        assertThat(adapter.hasTool("nope")).isFalse();
    }
}
