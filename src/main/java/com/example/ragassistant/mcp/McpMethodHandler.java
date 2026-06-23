package com.example.ragassistant.mcp;

import com.example.ragassistant.mcp.jsonrpc.JsonRpcError;
import com.example.ragassistant.mcp.jsonrpc.JsonRpcRequest;
import com.example.ragassistant.mcp.jsonrpc.JsonRpcResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * MCP method 디스패치: initialize / tools/list / tools/call / ping.
 * notification(id 없음)은 어떤 method든 응답하지 않는다(Optional.empty).
 */
@Component
@Profile("mcp-stdio")
public class McpMethodHandler {

    private final McpToolAdapter toolAdapter;

    public McpMethodHandler(McpToolAdapter toolAdapter) {
        this.toolAdapter = toolAdapter;
    }

    public Optional<JsonRpcResponse> handle(JsonRpcRequest req) {
        if (req.isNotification()) {
            return Optional.empty();
        }
        JsonNode id = req.id();
        String method = req.method();
        if (!StringUtils.hasText(method)) {
            return Optional.of(JsonRpcResponse.failure(id, JsonRpcError.invalidRequest("method 누락")));
        }
        return switch (method) {
            case "initialize" -> Optional.of(JsonRpcResponse.success(id, initializeResult(req.params())));
            case "tools/list" -> Optional.of(JsonRpcResponse.success(id, Map.of("tools", toolAdapter.listTools())));
            case "tools/call" -> Optional.of(handleToolsCall(id, req.params()));
            case "ping" -> Optional.of(JsonRpcResponse.success(id, Map.of()));
            default -> Optional.of(JsonRpcResponse.failure(id, JsonRpcError.methodNotFound(method)));
        };
    }

    private Map<String, Object> initializeResult(JsonNode params) {
        String protocolVersion = McpConstants.DEFAULT_PROTOCOL_VERSION;
        if (params != null && params.hasNonNull("protocolVersion")) {
            protocolVersion = params.get("protocolVersion").asText(protocolVersion);
        }
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", McpConstants.SERVER_NAME);
        serverInfo.put("version", McpConstants.SERVER_VERSION);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", protocolVersion);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", serverInfo);
        return result;
    }

    private JsonRpcResponse handleToolsCall(JsonNode id, JsonNode params) {
        if (params == null || !params.hasNonNull("name")) {
            return JsonRpcResponse.failure(id, JsonRpcError.invalidParams("params.name 누락"));
        }
        String name = params.get("name").asText();
        if (!StringUtils.hasText(name)) {
            return JsonRpcResponse.failure(id, JsonRpcError.invalidParams("params.name 비어있음"));
        }
        if (!toolAdapter.hasTool(name)) {
            return JsonRpcResponse.failure(id, JsonRpcError.invalidParams("알 수 없는 도구: " + name));
        }
        try {
            Map<String, Object> result = toolAdapter.callTool(name, params.get("arguments"));
            return JsonRpcResponse.success(id, result);
        } catch (Exception e) {
            return JsonRpcResponse.failure(id, JsonRpcError.internalError("도구 실행 오류: " + e.getMessage()));
        }
    }
}
