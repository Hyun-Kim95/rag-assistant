package com.example.ragassistant.llm.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * content에 흘러나온 도구 호출 JSON(gpt-oss·qwen 등)을 ToolCall로 승격하는 복구 로직 검증.
 */
class OpenAiCompatAgentClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("설명문 + 코드펜스 안의 list_documents JSON → 도구 호출로 승격")
    void recoversListDocumentsFromFencedJson() {
        String content = "업로드된 문서 목록을 확인해보겠습니다.\n\n```json\n{\"name\": \"list_documents\", \"arguments\": {}}\n```";

        List<ToolCall> calls = ToolCallEcho.recover(content, objectMapper);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("list_documents");
        assertThat(calls.get(0).argumentsJson()).isEqualTo("{}");
        assertThat(ToolCallEcho.contains(content)).isTrue();
    }

    @Test
    @DisplayName("search_documents JSON(인자 포함) → 인자까지 승격")
    void recoversSearchDocumentsWithArgs() {
        String content = "{\"name\": \"search_documents\", \"arguments\": {\"query\": \"pgvector 이유\"}}";

        List<ToolCall> calls = ToolCallEcho.recover(content, objectMapper);

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("search_documents");
        assertThat(calls.get(0).argumentsJson()).contains("pgvector");
    }

    @Test
    @DisplayName("일반 한국어 답변 → 승격하지 않음(오탐 방지)")
    void doesNotRecoverFromNormalAnswer() {
        String content = "pgvector를 선택한 이유는 PostgreSQL 하나로 벡터와 텍스트 검색을 모두 처리하기 위해서입니다.";

        List<ToolCall> calls = ToolCallEcho.recover(content, objectMapper);

        assertThat(calls).isEmpty();
        assertThat(ToolCallEcho.contains(content)).isFalse();
    }
}
