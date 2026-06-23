package com.example.ragassistant.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON-RPC 2.0 error 객체. code/message는 필수, data는 선택.
 * 표준 에러 코드 상수와 생성 헬퍼를 함께 제공한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcError(int code, String message, Object data) {

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    public static JsonRpcError of(int code, String message) {
        return new JsonRpcError(code, message, null);
    }

    public static JsonRpcError parseError(String message) {
        return of(PARSE_ERROR, message);
    }

    public static JsonRpcError invalidRequest(String message) {
        return of(INVALID_REQUEST, message);
    }

    public static JsonRpcError methodNotFound(String method) {
        return of(METHOD_NOT_FOUND, "Method not found: " + method);
    }

    public static JsonRpcError invalidParams(String message) {
        return of(INVALID_PARAMS, message);
    }

    public static JsonRpcError internalError(String message) {
        return of(INTERNAL_ERROR, message);
    }
}
