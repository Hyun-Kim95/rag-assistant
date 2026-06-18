package com.example.ragassistant.llm;

import java.util.function.Consumer;

/**
 * LLM chat 추론 경계(transport).
 * 소비자(RagService 등)는 이 인터페이스에만 의존하고 구현(Ollama)에는 의존하지 않는다.
 * <p>
 * 확장: SaaS LLM·Model Router로 늘리려면 이 인터페이스의 새 구현체를 추가하고
 * (예: RoutingChatModelClient 를 @Primary로) 빈 와이어링만 바꾼다. 소비자 코드는 불변.
 */
public interface ChatModelClient {

    /**
     * 동기 chat: 프롬프트 → 전체 답변
     */
    String chat(String prompt);

    /**
     * 스트리밍 chat: 토큰 조각마다 onDelta 호출, 전체 답변 반환
     */
    String streamChat(String prompt, Consumer<String> onDelta);
}
