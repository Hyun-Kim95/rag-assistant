package com.example.ragassistant.mcp;

/**
 * MCP 서버 메타 상수. protocolVersion은 클라이언트가 요청한 값이 있으면 그대로 echo하고,
 * 없으면 이 기본값을 사용한다(호환성 위해 널리 쓰이는 2024-11-05 고정).
 */
public final class McpConstants {

    private McpConstants() {
    }

    public static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";
    public static final String SERVER_NAME = "rag-assistant";
    public static final String SERVER_VERSION = "0.0.1-SNAPSHOT";
}
