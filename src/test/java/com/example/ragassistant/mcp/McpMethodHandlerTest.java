package com.example.ragassistant.mcp;

import com.example.ragassistant.agent.tool.AgentTool;
import com.example.ragassistant.agent.tool.ToolRegistry;
import com.example.ragassistant.agent.tool.ToolResult;
import com.example.ragassistant.mcp.jsonrpc.JsonRpcError;
import com.example.ragassistant.mcp.jsonrpc.JsonRpcRequest;
import com.example.ragassistant.mcp.jsonrpc.JsonRpcResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * McpMethodHandler: method 디스패치 검증.
 * initialize, tools/call 없는 도구·인자 누락, unknown method, notification 무응답.
 */
class McpMethodHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // search_documents 하나만 등록한 핸들러를 만든다. execute는 query를 그대로 되돌려
    // 도구 호출이 인자까지 전달됐는지 확인할 수 있게 한다(DB/Ollama 불필요).
    private McpMethodHandler handler() {
        AgentTool echo = new AgentTool() {
            @Override
            public String name() {
                return "search_documents";
            }

            @Override
            public String description() {
                return "검색";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query"));
            }

            @Override
            public ToolResult execute(JsonNode arguments) {
                return ToolResult.text("결과: " + arguments.path("query").asText(""));
            }
        };
        return new McpMethodHandler(new McpToolAdapter(new ToolRegistry(List.of(echo))));
    }

    // id를 가진 일반 요청(=notification 아님)을 만드는 헬퍼. id가 있으므로 응답이 와야 한다.
    private JsonRpcRequest req(String method, JsonNode params) {
        return new JsonRpcRequest("2.0", mapper.getNodeFactory().numberNode(1), method, params);
    }

    // initialize 응답에 protocolVersion·capabilities.tools·serverInfo.name이 규약대로 채워지는지. 클라이언트가 버전을 안 보내면 기본값을 쓴다.
    @Test
    void initialize_returnsProtocolVersionCapabilitiesServerInfo() {
        Optional<JsonRpcResponse> res = handler().handle(req("initialize", mapper.createObjectNode()));

        JsonRpcResponse response = res.orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result.get("protocolVersion")).isEqualTo(McpConstants.DEFAULT_PROTOCOL_VERSION);
        assertThat(result.get("capabilities")).isEqualTo(Map.of("tools", Map.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertThat(serverInfo.get("name")).isEqualTo("rag-assistant");
    }

    // 클라이언트가 protocolVersion을 보내면 그 값을 그대로 echo해야 신버전 클라이언트와도 핸드셰이크가 맞는다(기본값으로 덮어쓰지 않음).
    @Test
    void initialize_echoesClientRequestedProtocolVersion() {
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", "2025-06-18");

        Optional<JsonRpcResponse> res = handler().handle(req("initialize", params));

        JsonRpcResponse response = res.orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result.get("protocolVersion")).isEqualTo("2025-06-18");
    }

    // tools/list가 result.tools 배열로 등록된 도구를 노출하는지(여기선 1종).
    // params 없이도(null) 동작해야 한다.
    @Test
    void toolsList_exposesRegisteredTools() {
        Optional<JsonRpcResponse> res = handler().handle(req("tools/list", null));

        JsonRpcResponse response = res.orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertThat(tools).extracting(t -> t.get("name")).containsExactly("search_documents");
    }

    // name + arguments로 도구가 실행되고 error 없이 isError=false 결과가 오는지.
    @Test
    void toolsCall_succeeds() {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", "search_documents");
        ObjectNode args = params.putObject("arguments");
        args.put("query", "pgvector");

        Optional<JsonRpcResponse> res = handler().handle(req("tools/call", params));

        JsonRpcResponse response = res.orElseThrow();
        assertThat(response.error()).isNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result.get("isError")).isEqualTo(false);
    }

    // 미등록 도구는 결과가 아니라 -32602(invalid params) 에러로 응답해야 한다
    // (ToolRegistry.execute의 "알 수 없는 도구" 텍스트에 의존하지 않고 핸들러가 선판정).
    @Test
    void toolsCall_unknownToolReturnsInvalidParams() {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", "no_such_tool");

        Optional<JsonRpcResponse> res = handler().handle(req("tools/call", params));

        JsonRpcResponse response = res.orElseThrow();
        assertThat(response.result()).isNull();
        assertThat(response.error().code()).isEqualTo(JsonRpcError.INVALID_PARAMS);
    }

    // params.name 자체가 없으면 -32602. (인자 누락 같은 도구 도메인 오류는 별개로 도구가 텍스트로 처리하지만, name 누락은 프로토콜 레벨 오류다.)
    @Test
    void toolsCall_missingNameReturnsInvalidParams() {
        Optional<JsonRpcResponse> res = handler().handle(req("tools/call", mapper.createObjectNode()));

        assertThat(res.orElseThrow().error().code()).isEqualTo(JsonRpcError.INVALID_PARAMS);
    }

    // 지원하지 않는 method는 -32601(method not found)로 응답한다.
    @Test
    void unknownMethod_returnsMethodNotFound() {
        Optional<JsonRpcResponse> res = handler().handle(req("does/not/exist", null));

        assertThat(res.orElseThrow().error().code()).isEqualTo(JsonRpcError.METHOD_NOT_FOUND);
    }

    // ping은 error 없이 빈 result({})를 돌려줘야 한다(연결 확인용).
    @Test
    void ping_returnsEmptyResult() {
        Optional<JsonRpcResponse> res = handler().handle(req("ping", null));

        JsonRpcResponse response = res.orElseThrow();
        assertThat(response.error()).isNull();
        assertThat(response.result()).isEqualTo(Map.of());
    }

    // id 없는 notification(notifications/initialized 등)은 JSON-RPC 규약상 응답하지 않는다 → Optional.empty(). 응답을 보내면 stdout이 오염된다.
    @Test
    void notification_producesNoResponse() {
        JsonRpcRequest notification = new JsonRpcRequest("2.0", null, "notifications/initialized", null);

        Optional<JsonRpcResponse> res = handler().handle(notification);

        assertThat(res).isEmpty();
    }
}
