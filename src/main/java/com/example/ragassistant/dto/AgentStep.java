package com.example.ragassistant.dto;

import java.util.Map;

/**
 * agent 루프의 도구 호출 1건 추적(투명성·디버깅).
 *
 * @param index         1부터 증가하는 순번.
 * @param tool          호출한 도구 이름.
 * @param arguments     모델이 넘긴 인자.
 * @param resultSummary 결과 요약(전체가 아닌 짧은 요약).
 */
public record AgentStep(int index, String tool, Map<String, Object> arguments, String resultSummary) {
}
