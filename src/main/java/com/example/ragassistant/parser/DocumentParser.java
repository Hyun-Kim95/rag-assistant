package com.example.ragassistant.parser;

import com.example.ragassistant.exception.DocumentParseException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 업로드 파일을 RAG 파이프라인용 plain text로 변환.
 * 지원: .txt, .md, .pdf
 * PDF 고도화 :
 * - 페이지 단위 추출 + 구분자 (chunk·출처 힌트)
 * - sortByPosition (다단/표 순서 개선)
 * - 텍스트 레이어 없음 → DocumentParseException (OCR 미지원)
 * 미지원: 암호 PDF, 스캔 PDF(OCR), 표/레이아웃 완벽 복원
 */
public class DocumentParser {

    // Chunker·출처 snippet에서 페이지 경계를 식별하기 위한 마커
    static final String PAGE_MARKER_PREFIX = "--- Page ";
    static final String PAGE_MARKER_SUFFIX = " ---";

    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".pdf");
    }

    /**
     * @param filename 원본 파일명 (확장자로 파서 분기)
     * @param bytes    업로드 바이트 (DocumentService에서 empty 검사 후 호출)
     */
    public String parse(String filename, byte[] bytes) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return parsePdf(bytes);
        }
        // txt / md: UTF-8 plain text
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * PDFBox 3.x: Loader.loadPDF → extractTextByPage (페이지 마커 + sortByPosition)
     * try-with-resources로 PDDocument 누수 방지.
     */
    private String parsePdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return extractTextByPage(document);
        } catch (InvalidPasswordException e) {
            throw new DocumentParseException("암호로 보호된 PDF는 지원하지 않습니다.", e);
        } catch (IOException e) {
            throw new DocumentParseException("PDF를 읽을 수 없습니다. 파일이 손상되었을 수 있습니다.", e);
        }
    }

    /**
     * 전 페이지를 순회하며 텍스트를 합친다.
     * 빈 페이지는 건너뛰고, 한 글자도 없으면 스캔 PDF 등으로 간주해 거부한다.
     */
    private String extractTextByPage(PDDocument document) throws IOException {
        int pageCount = document.getNumberOfPages();
        if (pageCount == 0) {
            throw new DocumentParseException(
                    "PDF에서 텍스트를 추출할 수 없습니다. 스캔 PDF(이미지만)는 OCR 미지원입니다.");
        }
        PDFTextStripper stripper = new PDFTextStripper();
        // false(기본): PDF 내부 저장 순서 → 다단/표에서 읽기 순서 깨질 수 있음
        // true: y/x 좌표 기준 정렬
        stripper.setSortByPosition(true);
        StringBuilder result = new StringBuilder();
        for (int page = 1; page <= pageCount; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            String pageText = normalizePageText(stripper.getText(document));
            if (pageText.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append("\n\n");
            }
            result.append(PAGE_MARKER_PREFIX).append(page).append(PAGE_MARKER_SUFFIX);
            result.append("\n\n");
            result.append(pageText);
        }
        if (result.isEmpty()) {
            // 이미지 스캔 PDF: PDFBox는 페이지는 있지만 getText()가 전부 빈 문자열
            throw new DocumentParseException(
                    "PDF에서 텍스트를 추출할 수 없습니다. 스캔 PDF(이미지만)는 OCR 미지원입니다.");
        }
        return result.toString();
    }

    /**
     * 페이지 단위 후처리: 양끝 공백 제거, 연속 빈 줄 3개 이상 → 2개로 축소.
     * Chunker 입력 길이·노이즈를 줄이되 문단 구조는 유지.
     */
    private static String normalizePageText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.strip();
        // \r\n 통일 (txt/md Chunker.normalize()와 정책 맞춤)
        trimmed = trimmed.replace("\r\n", "\n");
        return trimmed.replaceAll("\n{3,}", "\n\n");
    }
}
