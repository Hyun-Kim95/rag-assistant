package com.example.ragassistant.eval;

/**
 * 평가 실행 모드.
 * - RAG_ON:  Retriever → PromptBuilder → Ollama (프로덕션 경로)
 * - RAG_OFF: 검색 없이 LLM만 (v2 대조군, ablation)
 */
public enum EvalMode {
    RAG_ON,
    RAG_OFF
}
