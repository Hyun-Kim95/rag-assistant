package com.example.ragassistant.mcp.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 요청 1건.
 * - id가 없으면(null/누락/JSON null) notification으로 보고 응답하지 않는다.
 * - params는 원본 JsonNode 그대로 보관해 method 핸들러가 해석한다.
 */
public record JsonRpcRequest(String jsonrpc, JsonNode id, String method, JsonNode params) {

    public boolean isNotification() {
        return id == null || id.isNull() || id.isMissingNode();
    }
}
