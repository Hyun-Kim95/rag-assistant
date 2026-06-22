package com.example.ragassistant.llm.agent;

import com.example.ragassistant.config.AgentProperties;
import com.example.ragassistant.exception.AllProvidersUnavailableException;
import com.example.ragassistant.exception.LlmResponseException;
import com.example.ragassistant.exception.LlmUnavailableException;
import com.example.ragassistant.exception.UnknownProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 다중 agent provider 라우팅 + 폴백.
 * - @Primary: 소비자(AgentOrchestrator)의 단일 AgentChatClient 주입을 이 라우터로 해소.
 * - 체인: [요청 provider] + agent.provider-order (중복 제거). 기본 order = [groq, ollama]
 * - 폴백 대상: LlmUnavailableException(연결)·LlmResponseException(응답).
 * - 메시지 히스토리가 provider 중립이라 호출 단위 폴백이 안전.
 */
@Service
@Primary
public class RoutingAgentChatClient implements AgentChatClient {

    private static final Logger log = LoggerFactory.getLogger(RoutingAgentChatClient.class);

    private final Map<String, AgentChatClient> byName;
    private final AgentProperties props;

    public RoutingAgentChatClient(List<AgentChatClient> all, AgentProperties props) {
        Map<String, AgentChatClient> map = new LinkedHashMap<>();
        for (AgentChatClient c : all) {
            if (c instanceof RoutingAgentChatClient) {
                continue; // self 제외(순환 주입 방지)
            }
            AgentChatClient prev = map.put(c.name(), c);
            if (prev != null) {
                throw new IllegalStateException("agent provider name 중복: " + c.name());
            }
        }
        this.byName = map;
        this.props = props;
    }

    @Override
    public String name() {
        return "agent-router";
    }

    @Override
    public AgentTurn chat(List<AgentMessage> messages, List<ToolSpec> tools) {
        return chat(messages, tools, null);
    }

    @Override
    public AgentTurn chat(List<AgentMessage> messages, List<ToolSpec> tools, String requestedProvider) {
        List<String> chain = chain(requestedProvider);
        for (String n : chain) {
            AgentChatClient provider = byName.get(n);
            if (provider == null || !provider.available()) {
                continue;
            }
            try {
                return provider.chat(messages, tools);
            } catch (LlmUnavailableException | LlmResponseException e) {
                log.warn("agent provider 실패, 폴백 시도: provider={} cause={}", n, e.toString());
            }
        }
        throw new AllProvidersUnavailableException("모든 agent provider 사용 불가. chain=" + chain);
    }

    private List<String> chain(String requestedProvider) {
        List<String> chain = new ArrayList<>();
        if (StringUtils.hasText(requestedProvider)) {
            if (!byName.containsKey(requestedProvider)) {
                throw new UnknownProviderException("알 수 없는 provider: " + requestedProvider);
            }
            chain.add(requestedProvider);
        }
        if (props.providerOrder() != null) {
            chain.addAll(props.providerOrder());
        }
        return chain.stream().distinct().toList();
    }
}
