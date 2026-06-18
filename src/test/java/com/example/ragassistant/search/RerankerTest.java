package com.example.ragassistant.search;

import com.example.ragassistant.domain.SearchHit;
import com.example.ragassistant.observability.QueryTelemetryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * Reranker 단위 테스트.
 * 실제 TEI 없이 MockRestServiceServer로 /rerank 응답을 가짜로 주입한다.
 * 검증 포인트: 재정렬·rerank score 교체·topN 컷·실패 시 fallback(원본 순서 유지).
 */
class RerankerTest {

    private MockRestServiceServer server;
    private Reranker reranker;

    private final SearchHit hit0 = new SearchHit(10L, "a.md", "text0", 0.50);
    private final SearchHit hit1 = new SearchHit(11L, "b.md", "text1", 0.40);
    private final SearchHit hit2 = new SearchHit(12L, "c.md", "text2", 0.30);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://tei");
        server = MockRestServiceServer.bindTo(builder).build();
        // 활성 telemetry 컨텍스트 없이 record*는 no-op → 실제 인스턴스로 충분
        reranker = new Reranker(builder.build(), new QueryTelemetryContext());
    }

    /**
     * TEI가 매긴 순서대로 재정렬되고, score가 rerank score로 교체되며, topN으로 잘린다.
     */
    @Test
    void rerank_reordersReplacesScoreAndLimits() {
        // TEI는 score 내림차순 [{index,score}] 반환: 후보 index 2 → 0 → 1 순
        server.expect(requestTo("http://tei/rerank"))
                .andExpect(method(POST))
                .andRespond(withSuccess(
                        "[{\"index\":2,\"score\":0.9},{\"index\":0,\"score\":0.7},{\"index\":1,\"score\":0.1}]",
                        MediaType.APPLICATION_JSON));

        List<SearchHit> result = reranker.rerank("q", List.of(hit0, hit1, hit2), 2);

        // topN=2 → 상위 2개만
        assertThat(result).hasSize(2);
        // 순서: index 2(hit2) → index 0(hit0)
        assertThat(result.get(0).getChunkId()).isEqualTo(12L);
        assertThat(result.get(1).getChunkId()).isEqualTo(10L);
        // score는 rerank score로 교체
        assertThat(result.get(0).getScore()).isEqualTo(0.9);
        assertThat(result.get(1).getScore()).isEqualTo(0.7);
        server.verify();
    }

    /**
     * TEI 실패(5xx 등) 시 원본 순서를 유지하고 topN으로만 자른다 (fallback).
     */
    @Test
    void rerank_whenTeiFails_fallsBackToOriginalOrder() {
        server.expect(requestTo("http://tei/rerank"))
                .andRespond(withServerError());

        List<SearchHit> result = reranker.rerank("q", List.of(hit0, hit1, hit2), 2);

        // 원본 순서 그대로 상위 2개, score도 원본 유지
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getChunkId()).isEqualTo(10L);
        assertThat(result.get(1).getChunkId()).isEqualTo(11L);
        assertThat(result.get(0).getScore()).isEqualTo(0.50);
    }

    /**
     * 후보가 비면 TEI 호출 없이 빈 목록 그대로 반환.
     */
    @Test
    void rerank_emptyCandidates_returnsEmptyWithoutCall() {
        List<SearchHit> result = reranker.rerank("q", List.of(), 6);
        assertThat(result).isEmpty();
        // 호출이 없었으므로 server.verify()는 expect가 없어 통과
        server.verify();
    }
}
