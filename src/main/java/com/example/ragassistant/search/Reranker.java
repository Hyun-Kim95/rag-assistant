package com.example.ragassistant.search;

import com.example.ragassistant.domain.SearchHit;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

/**
 * TEI cross-encoder reranker 호출.
 * 후보(candidate)를 질문 기준으로 재정렬 → 상위 topN 반환.
 * TEI 실패/타임아웃 시 원본 순서 유지 + topN 컷 (fallback) → RAG가 죽지 않음.
 */
@Component
public class Reranker {

    private static final Logger log = LoggerFactory.getLogger(Reranker.class);

    private final RestClient rerankerRestClient;

    public Reranker(RestClient rerankerRestClient) {
        this.rerankerRestClient = rerankerRestClient;
    }

    public List<SearchHit> rerank(String query, List<SearchHit> candidates, int topN) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        try {
            List<String> texts = candidates.stream().map(SearchHit::getContent).toList();

            RerankScore[] scores = rerankerRestClient.post()
                    .uri("/rerank")
                    .body(new RerankRequest(query, texts))
                    .retrieve()
                    .body(RerankScore[].class);

            if (scores == null || scores.length == 0) {
                log.warn("TEI rerank 응답이 비어 있음 — 원본 순서로 fallback");
                return fallback(candidates, topN);
            }

            // 응답은 score 내림차순. index로 원본 후보 매핑 + score를 rerank score로 교체.
            return Arrays.stream(scores)
                    .filter(s -> s.index() >= 0 && s.index() < candidates.size())
                    .limit(topN)
                    .map(s -> candidates.get(s.index()).withScore(s.score()))
                    .toList();

        } catch (Exception ex) {
            // 연결 실패·타임아웃·파싱 오류 모두 fallback (RAG 중단 방지)
            log.warn("TEI rerank 실패 — 원본 순서로 fallback: {}", ex.toString());
            return fallback(candidates, topN);
        }
    }

    private List<SearchHit> fallback(List<SearchHit> candidates, int topN) {
        return candidates.stream().limit(topN).toList();
    }

    // 요청: {"query": "...", "texts": ["...", "..."]}
    record RerankRequest(String query, List<String> texts) {
    }

    // 응답 원소: {"index": 0, "score": 0.99}  (return_text 등 추가 필드는 무시)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RerankScore(int index, double score) {
    }
}
