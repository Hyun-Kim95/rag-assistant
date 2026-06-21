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

    /**
     * provider 힌트를 받는 chat. 단일 provider 구현체는 힌트를 무시한다(기본 구현).
     * RoutingChatModelClient 만 override 해 해당 provider를 체인 맨 앞에 둔다.
     */
    default String chat(String prompt, String provider) {
        return chat(prompt);
    }

    /**
     * 라우팅 분류용 텍스트(routingText)를 프롬프트와 분리해 받는 chat.
     * - prompt: 모델에 실제로 보낼 (증강된) 프롬프트.
     * - routingText: 난이도 분류 등 라우팅 판단에 쓸 원문(보통 사용자 질문).
     * 단일 provider 구현체는 routingText를 무시한다(기본 구현).
     */
    default String chat(String prompt, String provider, String routingText) {
        return chat(prompt, provider);
    }

    /**
     * 지정 provider만 호출하고 fallback하지 않는 chat(벤치마크 측정 타당성용).
     * - 해당 provider가 실패하면 예외를 그대로 전파(다른 leg로 폴백하지 않음).
     * - 단일 provider 구현체는 provider 힌트를 무시하고 자기 자신을 호출한다(기본 구현).
     * RoutingChatModelClient만 override 해 체인/폴백 없이 단일 leg를 호출한다.
     */
    default String chatStrict(String prompt, String provider) {
        return chat(prompt, provider);
    }

    /**
     * 라우팅·헬스용 provider 식별자. 예: "ollama-7b", "groq".
     * RoutingChatModelClient 가 byName 맵 키와 fallback-order 매칭에 사용한다.
     */
    String name();

    /**
     * 헬스·라우팅용 가용성(설정 수준). 기본 true.
     * SaaS 구현체는 enabled + api-key 존재 여부를 반영한다(실제 도달성 핑은 아님).
     */
    default boolean available() {
        return true;
    }
}
