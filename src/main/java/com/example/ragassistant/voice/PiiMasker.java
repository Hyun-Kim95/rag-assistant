package com.example.ragassistant.voice;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 통화 로그 저장 전 개인정보(PII) 마스킹.
 * 구체 패턴(이메일·주민·카드·전화)을 먼저 치환하고, 남은 긴 숫자열을 일반화한다.
 * 순서가 중요: 카드(16자리)·주민(13자리)·전화를 긴 숫자열 치환보다 앞에 둔다.
 */
@Component
public class PiiMasker {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    // 주민등록번호: 6자리-성별식별(1~4)+6자리
    private static final Pattern RRN = Pattern.compile("\\d{6}[-\\s]?[1-4]\\d{6}");
    // 카드번호: 4-4-4-4 (구분자 허용)
    private static final Pattern CARD = Pattern.compile("\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}");
    // 휴대전화: 01X-XXXX-XXXX (구분자 허용)
    private static final Pattern PHONE = Pattern.compile("01[0-9][-\\s]?\\d{3,4}[-\\s]?\\d{4}");
    // 그 외 긴 숫자열(계좌 등) 일반화
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{7,}");

    public String mask(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String result = text;
        result = EMAIL.matcher(result).replaceAll("[이메일]");
        result = RRN.matcher(result).replaceAll("[주민번호]");
        result = CARD.matcher(result).replaceAll("[카드번호]");
        result = PHONE.matcher(result).replaceAll("[전화번호]");
        result = LONG_NUMBER.matcher(result).replaceAll("[번호]");
        return result;
    }
}
