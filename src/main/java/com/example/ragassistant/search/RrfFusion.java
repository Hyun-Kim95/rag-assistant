package com.example.ragassistant.search;

import com.example.ragassistant.domain.SearchHit;

import java.util.*;

/**
 * Reciprocal Rank Fusion (RRF).
 * 벡터 검색 결과와 키워드 검색 결과의 순위만 합쳐서, 최종 top-k chunk 목록을 만든다.
 * 벡터 cosine score(0~1)와 lexical similarity(0~1)는 스케일·분포가 달라서 단순 가중합보다 순위 기반 병합이 안정적이다.
 * 공식: {@code rrf(d) = Σ 1 / (k + rank_i(d))} — rank는 1부터.
 * k는 보통 60(Elasticsearch·논문 관례).
 */
public final class RrfFusion {
    private RrfFusion() {
    }

    /**
     * @param vectorHits  cosine 순위 (이미 score 내림차순)
     * @param lexicalHits trigram similarity 순위
     * @param k           RRF 상수 (rag.rrf-k)
     * @param limit       최종 반환 개수 (rag.top-k)
     */
    public static List<SearchHit> fuse(List<SearchHit> vectorHits, List<SearchHit> lexicalHits, int k, int limit) {
        Map<Long, Acc> byChunk = new HashMap<>();
        accumulate(byChunk, vectorHits, k, true);
        accumulate(byChunk, lexicalHits, k, false);
        List<Acc> merged = new ArrayList<>(byChunk.values());
        // .thenComparingDouble(...).reversed()는 체인 전체를 뒤집음 → 보조 키는 thenComparing(내부 Comparator)로
        merged.sort(Comparator
                .comparingDouble((Acc acc) -> acc.rrfScore).reversed()
                .thenComparing(Comparator.comparingDouble(Acc::bestLegScore).reversed())
                .thenComparingLong((Acc acc) -> acc.chunkId));
        return merged.stream()
                .limit(limit)
                .map(Acc::toSearchHit)
                .toList();
    }

    private static void accumulate(Map<Long, Acc> byChunk, List<SearchHit> hits, int k, boolean fromVector) {
        for (int i = 0; i < hits.size(); i++) {
            SearchHit hit = hits.get(i);
            int rank = i + 1;
            double rrfInc = 1.0 / (k + rank);
            byChunk.compute(hit.getChunkId(), (id, acc) -> {
                if (acc == null) {
                    acc = new Acc(hit.getChunkId(), hit.getDocumentName(), hit.getContent());
                }
                acc.rrfScore += rrfInc;
                if (fromVector) {
                    acc.vectorScore = Math.max(acc.vectorScore, hit.getScore());
                } else {
                    acc.lexicalScore = Math.max(acc.lexicalScore, hit.getScore());
                }
                return acc;
            });
        }
    }

    /**
     * chunk 단위로 점수를 모으는 임시 객체
     * -> 같은 chunkId가 vector·lexical 양쪽에 나올 수 있기 때문.
     * (출처 snippet 옆 숫자 — 사용자에게 익숙한 0~1 유사도 유지)
     */
    private static final class Acc {
        private final Long chunkId;
        private final String documentName;
        private final String content;
        private double rrfScore;
        private double vectorScore;
        private double lexicalScore;

        private Acc(Long chunkId, String documentName, String content) {
            this.chunkId = chunkId;
            this.documentName = documentName;
            this.content = content;
        }

        private double bestLegScore() {
            return vectorScore > 0 ? vectorScore : lexicalScore;
        }

        // API 응답용, score: 벡터 score 우선, 없으면 lexical.
        private SearchHit toSearchHit() {
            double displayScore = vectorScore > 0 ? vectorScore : lexicalScore;
            return new SearchHit(chunkId, documentName, content, displayScore);
        }
    }
}
