package com.example.ragassistant.service;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 시스템 FAQ 문서·chunk·embedding을 보장한다.
 * - 최초: documents INSERT + indexRawChunks
 * - FAQ 문구 변경: 기존 문서 DELETE(CASCADE) 후 재생성
 * - 동일: 스킵
 */
@Component
public class FaqBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FaqBootstrap.class);
    private final FaqIndexService faqIndexService;

    public FaqBootstrap(FaqIndexService faqIndexService) {
        this.faqIndexService = faqIndexService;
    }

    @Override
    public void run(@Nullable ApplicationArguments args) {
        try {
            faqIndexService.ensureFaqIndexed();
        } catch (Exception ex) {
            // Ollama 미기동 시에도 앱은 뜨게; FAQ는 다음 기동 때 재시도
            log.warn("시스템 FAQ 인덱싱 실패 — Ollama·DB 확인 후 재기동하세요", ex);
        }
    }
}
