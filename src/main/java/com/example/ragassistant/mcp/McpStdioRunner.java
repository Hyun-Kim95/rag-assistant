package com.example.ragassistant.mcp;

import com.example.ragassistant.mcp.jsonrpc.JsonRpcError;
import com.example.ragassistant.mcp.jsonrpc.JsonRpcRequest;
import com.example.ragassistant.mcp.jsonrpc.JsonRpcResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * stdio 전송 MCP 서버 루프. mcp-stdio 프로파일에서만 활성.
 * - stdin: 개행 구분 JSON-RPC 메시지(UTF-8). 한 줄 = 한 메시지.
 * - stdout: JSON-RPC 응답 전용(개행 구분). 로그는 logback-spring.xml에서 stderr로 분리.
 * HIGHEST_PRECEDENCE로 가장 먼저 실행해 stdin을 점유 → DB/Ollama 의존 부트스트랩(FaqBootstrap 등)
 * 보다 앞서 핸드셰이크가 가능(DB 없이도 initialize/tools/list/ping 동작).
 */
@Component
@Profile("mcp-stdio")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class McpStdioRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpStdioRunner.class);

    private final McpMethodHandler methodHandler;
    private final ObjectMapper objectMapper;

    public McpStdioRunner(McpMethodHandler methodHandler, ObjectMapper objectMapper) {
        this.methodHandler = methodHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(@Nullable ApplicationArguments args) throws Exception {
        // FileDescriptor.out으로 직접 써서 System.out 재할당과 무관하게 stdout을 깨끗이 유지.
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        log.info("MCP stdio 서버 시작 (protocolVersion={})", McpConstants.DEFAULT_PROTOCOL_VERSION);
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            process(line).ifPresent(response -> writeLine(out, response));
        }
        log.info("MCP stdio 입력 종료(EOF) — 서버 종료");
    }

    private Optional<JsonRpcResponse> process(String line) {
        JsonNode node;
        try {
            node = objectMapper.readTree(line);
        } catch (JsonProcessingException e) {
            return Optional.of(JsonRpcResponse.failure(NullNode.getInstance(),
                    JsonRpcError.parseError("JSON 파싱 실패: " + e.getOriginalMessage())));
        }
        if (node == null || !node.isObject()) {
            return Optional.of(JsonRpcResponse.failure(NullNode.getInstance(),
                    JsonRpcError.invalidRequest("요청은 JSON object여야 합니다")));
        }
        JsonNode id = node.get("id");
        JsonNode idForError = (id == null) ? NullNode.getInstance() : id;
        try {
            JsonRpcRequest req = new JsonRpcRequest(
                    node.path("jsonrpc").asText("2.0"),
                    id,
                    node.path("method").asText(null),
                    node.get("params"));
            return methodHandler.handle(req);
        } catch (Exception e) {
            log.warn("요청 처리 중 오류", e);
            return Optional.of(JsonRpcResponse.failure(idForError,
                    JsonRpcError.internalError("내부 오류: " + e.getMessage())));
        }
    }

    private void writeLine(PrintStream out, JsonRpcResponse response) {
        try {
            // 개행 구분 프레이밍: 메시지 사이 단일 '\n'(플랫폼 줄바꿈 의존 금지).
            out.print(objectMapper.writeValueAsString(response));
            out.print('\n');
            out.flush();
        } catch (JsonProcessingException e) {
            log.error("응답 직렬화 실패", e);
        }
    }
}
