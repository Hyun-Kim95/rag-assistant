package com.example.ragassistant.llm.agent;

import java.util.List;

/**
 * tool calling 가능한 chat transport 경계.
 * 기존 ChatModelClient(텍스트 in/out)와 분리
 * 소비자(AgentOrchestrator)는 이 인터페이스에만 의존한다.
 */
public interface AgentChatClient {

    /**
     * 대화 히스토리 + 도구 스펙 → 모델의 한 턴 응답
     */
    AgentTurn chat(List<AgentMessage> messages, List<ToolSpec> tools);

    /**
     * provider 힌트를 받는 chat. 단일 leg 구현체는 힌트를 무시한다(기본 구현).
     * RoutingAgentChatClient만 override 해 해당 provider를 체인 맨 앞에 둔다.
     */
    default AgentTurn chat(List<AgentMessage> messages, List<ToolSpec> tools, String provider) {
        return chat(messages, tools);
    }

    /**
     * 라우팅·헬스용 식별자. 예: "groq", "ollama".
     */
    String name();

    /**
     * 헬스·라우팅용 가용성(설정 수준). 기본 true.
     */
    default boolean available() {
        return true;
    }
}
