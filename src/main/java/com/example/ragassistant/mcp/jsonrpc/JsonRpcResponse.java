package com.example.ragassistant.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 응답 1건. 성공이면 result, 실패면 error 중 하나만 직렬화한다.
 * id는 요청 id를 그대로 echo하며, 결정할 수 없을 때(parse error)는 JSON null을 보낸다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"jsonrpc", "id", "result", "error"})
public record JsonRpcResponse(String jsonrpc, JsonNode id, Object result, JsonRpcError error) {

    private static final String VERSION = "2.0";

    public static JsonRpcResponse success(JsonNode id, Object result) {
        return new JsonRpcResponse(VERSION, id, result, null);
    }

    public static JsonRpcResponse failure(JsonNode id, JsonRpcError error) {
        return new JsonRpcResponse(VERSION, id, null, error);
    }
}
