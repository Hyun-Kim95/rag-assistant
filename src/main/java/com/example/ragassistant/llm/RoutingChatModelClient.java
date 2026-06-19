package com.example.ragassistant.llm;

import com.example.ragassistant.config.RoutingProperties;
import com.example.ragassistant.exception.AllProvidersUnavailableException;
import com.example.ragassistant.exception.LlmResponseException;
import com.example.ragassistant.exception.LlmUnavailableException;
import com.example.ragassistant.exception.UnknownProviderException;
import com.example.ragassistant.observability.QueryTelemetryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 다중 chat provider 라우팅 + 폴백.
 * - @Primary: 소비자(RagService 등)의 단일 ChatModelClient 주입을 이 라우터로 해소.
 * - chain: [defaultProvider, ...fallbackOrder] 1회 순회(중복 제거).
 * - 폴백 대상: LlmUnavailableException(연결)·LlmResponseException(응답). 동기 chat만.
 * - streamChat: 폴백 없이 사용 가능한 첫 provider 1개만
 */
@Service
@Primary
public class RoutingChatModelClient implements ChatModelClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingChatModelClient.class);

    private final Map<String, ChatModelClient> byName;
    private final RoutingProperties routing;
    private final QueryTelemetryContext telemetry;

    public RoutingChatModelClient(List<ChatModelClient> all,
                                  RoutingProperties routing,
                                  QueryTelemetryContext telemetry) {
        Map<String, ChatModelClient> map = new LinkedHashMap<>();
        for (ChatModelClient c : all) {
            if (c instanceof RoutingChatModelClient) {
                continue; // self 제외(순환 주입 방지)
            }
            ChatModelClient prev = map.put(c.name(), c);
            if (prev != null) {
                throw new IllegalStateException("chat provider name 중복: " + c.name());
            }
        }
        this.byName = map;
        this.routing = routing;
        this.telemetry = telemetry;
    }

    @Override
    public String name() {
        return "router";
    }

    @Override
    public String chat(String prompt) {
        return chat(prompt, null);
    }

    @Override
    public String chat(String prompt, String requestedProvider) {
        List<String> chain = chain(requestedProvider);
        boolean fellBack = false;
        for (String n : chain) {
            ChatModelClient provider = byName.get(n);
            if (provider == null || !provider.available()) {
                continue;
            }
            try {
                String answer = provider.chat(prompt);
                telemetry.recordProvider(n);
                if (fellBack) {
                    telemetry.recordFallbackUsed();
                }
                return answer;
            } catch (LlmUnavailableException | LlmResponseException e) {
                log.warn("chat provider 실패, 폴백 시도: provider={} cause={}", n, e.toString());
                fellBack = true;
            }
        }
        throw new AllProvidersUnavailableException("모든 chat provider 사용 불가. chain=" + chain);
    }

    @Override
    public String streamChat(String prompt, Consumer<String> onDelta) {
        // 폴백 없음. 사용 가능한 첫 provider 1개만.
        for (String n : chain(null)) {
            ChatModelClient provider = byName.get(n);
            if (provider == null || !provider.available()) {
                continue;
            }
            telemetry.recordProvider(n);
            return provider.streamChat(prompt, onDelta);
        }
        throw new AllProvidersUnavailableException("사용 가능한 chat provider 없음(stream)");
    }

    /** [요청 provider?] + defaultProvider + fallbackOrder — 중복 제거. */
    private List<String> chain(String requestedProvider) {
        List<String> chain = new ArrayList<>();
        if (StringUtils.hasText(requestedProvider)) {
            if (!byName.containsKey(requestedProvider)) {
                throw new UnknownProviderException("알 수 없는 provider: " + requestedProvider);
            }
            chain.add(requestedProvider);
        }
        if (StringUtils.hasText(routing.defaultProvider())) {
            chain.add(routing.defaultProvider());
        }
        if (routing.fallbackOrder() != null) {
            chain.addAll(routing.fallbackOrder());
        }
        return chain.stream().distinct().toList();
    }
}
