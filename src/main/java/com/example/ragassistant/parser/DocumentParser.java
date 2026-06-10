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
 * .txt, .md, .pdf
 */
public class DocumentParser {

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
     * PDFBox 3.x: Loader.loadPDF → PDFTextStripper
     * try-with-resources로 PDDocument 누수 방지.
     */
    private String parsePdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // 1차: 전 페이지, 기본 순서. 페이지 범위·정렬은 Step 10 고도화에서.
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new DocumentParseException("암호로 보호된 PDF는 지원하지 않습니다.", e);
        } catch (IOException e) {
            throw new DocumentParseException("PDF를 읽을 수 없습니다. 파일이 손상되었을 수 있습니다.", e);
        }
    }
}
