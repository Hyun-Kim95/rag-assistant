package com.example.ragassistant.faq;

import java.util.List;

/**
 * 시스템 FAQ chunk 텍스트 SSOT.
 * 인덱싱 시 Chunker를 거치지 않고 chunk 1개 = 항목 1개로 저장한다.
 * 문구 변경 시 기동마다 본문 비교 후 자동 재인덱싱된다.
 */
public final class FaqCatalog {
    /**
     * 사용자 업로드·DELETE와 구분하는 예약 문서명
     */
    public static final String DOCUMENT_NAME = "__SYSTEM_FAQ.md";
    public static final String CONTENT_TYPE = "text/markdown";

    private FaqCatalog() {
    }

    public static boolean isFaqDocument(String name) {
        return DOCUMENT_NAME.equals(name);
    }

    /**
     * 검색·임베딩에 쓰일 FAQ chunk 목록 (순서 = chunk_index).
     * 질문 표현의 동의어를 chunk 안에 함께 넣어 retrieval 안정화.
     */
    public static List<String> chunks() {
        return List.of(
                // hits empty / no-answer 정책
                """
                        [정책] 검색 hit가 없을 때 앱 동작
                        Retriever는 질문을 embedding한 뒤 pgvector cosine top-k 검색을 하고, \
                        min-score(0.2) 이상인 chunk만 hits로 남긴다.
                        hits가 비어 있으면(retrieval miss, 검색 결과 없음, min-score 미달) \
                        RagService.chat()은 Ollama LLM을 호출하지 않는다.
                        즉시 no-answer를 반환한다: grounded=false, sources=[], \
                        메시지「문서에서 확인할 수 없는 질문입니다.」
                        이는 환각 방지와 비용 절감을 위한 정책이다.
                        """.stripIndent().trim(),
                // chunk·retrieval 설정
                """
                        [설정] RAG chunk 및 retrieval 파라미터
                        chunk-size: 450, chunk-overlap: 150, top-k: 10, min-score: 0.2.
                        embedding dimension: 768. 설정 파일: application.yml의 rag 섹션.
                        """.stripIndent().trim()
        );
    }

    /**
     * documents.content에 저장할 전체 본문 (동기화·비교용)
     */
    public static String fullContent() {
        return String.join("\n\n---\n\n", chunks());
    }
}
