package io.modelaudit.chatmodel.audit.core.compliance.pii;

import java.util.regex.Pattern;

public final class KoreanBusinessNoDetector implements PiiDetector {

    // 3-2-5 사업자등록번호 (체크섬 검증은 v0.2)
    private static final Pattern PATTERN = Pattern.compile(
            "(?<!\\d)\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{5}(?!\\d)");

    private static final String MASK = "[MASKED:bizno:04]";

    @Override
    public String id() {
        return "kr-business-no";
    }

    @Override
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return PATTERN.matcher(input).replaceAll(MASK);
    }
}
