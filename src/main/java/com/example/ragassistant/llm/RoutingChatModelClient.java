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
 * - 전략(routing-strategy):
 * - fixed: chain = [defaultProvider, ...fallbackOrder] (중복 제거).
 * - difficulty: 분류기로 1차 leg 선택 → [picked, defaultProvider, ...fallbackOrder].
 * - 명시 provider 요청은 전략보다 우선(분류 생략).
 * - 폴백 대상: LlmUnavailableException(연결)·LlmResponseException(응답). 동기 chat만.
 * - streamChat: 전략·폴백 없이 fixed 체인의 첫 가용 provider 1개만.
 */
@Service
@Primary
public class RoutingChatModelClient implements ChatModelClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingChatModelClient.class);

    private final Map<String, ChatModelClient> byName;
    private final RoutingProperties routing;
    private final QueryTelemetryContext telemetry;
    private final DifficultyClassifier classifier;

    public RoutingChatModelClient(List<ChatModelClient> all,
                                  RoutingProperties routing,
                                  QueryTelemetryContext telemetry,
                                  DifficultyClassifier classifier) {
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
        this.classifier = classifier;
    }

    @Override
    public String name() {
        return "router";
    }

    @Override
    public String chat(String prompt) {
        return chat(prompt, null, prompt);
    }

    @Override
    public String chat(String prompt, String requestedProvider) {
        return chat(prompt, requestedProvider, prompt);
    }

    @Override
    public String chat(String prompt, String requestedProvider, String routingText) {
        String picked = pickByStrategy(routingText, requestedProvider);
        List<String> chain = chain(requestedProvider, picked);
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
    public String chatStrict(String prompt, String provider) {
        // provider 미지정이면 strict 의미가 없으므로 일반 라우팅으로 위임.
        if (!StringUtils.hasText(provider)) {
            return chat(prompt);
        }
        ChatModelClient leg = byName.get(provider);
        if (leg == null) {
            throw new UnknownProviderException("알 수 없는 provider: " + provider);
        }
        if (!leg.available()) {
            throw new AllProvidersUnavailableException("provider 사용 불가(strict, fallback 없음): " + provider);
        }
        // 폴백 없음 — 실패하면 예외 그대로 전파(측정 시 해당 provider 실패로 기록되도록).
        String answer = leg.chat(prompt);
        telemetry.recordProvider(provider);
        return answer;
    }

    @Override
    public String streamChat(String prompt, Consumer<String> onDelta) {
        // 전략·폴백 없음. fixed 체인의 첫 가용 provider 1개만.
        for (String n : chain(null, null)) {
            ChatModelClient provider = byName.get(n);
            if (provider == null || !provider.available()) {
                continue;
            }
            telemetry.recordProvider(n);
            return provider.streamChat(prompt, onDelta);
        }
        throw new AllProvidersUnavailableException("사용 가능한 chat provider 없음(stream)");
    }

    /**
     * difficulty 전략일 때 분류 결과로 1차 leg를 고른다.
     * - 명시 requestedProvider가 있으면 분류 생략(명시 우선).
     * - fixed 전략이면 null.
     */
    private String pickByStrategy(String prompt, String requestedProvider) {
        if (StringUtils.hasText(requestedProvider)) {
            return null;
        }
        if (!"difficulty".equalsIgnoreCase(routing.routingStrategy())) {
            return null;
        }
        RoutingProperties.Difficulty d = routing.difficulty();
        if (d == null) {
            return null;
        }
        DifficultyTier tier = classifier.classify(prompt);
        telemetry.recordDifficulty(tier.name());
        return tier == DifficultyTier.EASY ? d.easyProvider() : d.hardProvider();
    }

    /**
     * [요청 provider | 난이도 picked] + defaultProvider + fallbackOrder — 중복 제거.
     */
    private List<String> chain(String requestedProvider, String picked) {
        List<String> chain = new ArrayList<>();
        if (StringUtils.hasText(requestedProvider)) {
            if (!byName.containsKey(requestedProvider)) {
                throw new UnknownProviderException("알 수 없는 provider: " + requestedProvider);
            }
            chain.add(requestedProvider);
        } else if (StringUtils.hasText(picked)) {
            chain.add(picked); // 미등록 이름이면 루프에서 skip → default가 처리
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
