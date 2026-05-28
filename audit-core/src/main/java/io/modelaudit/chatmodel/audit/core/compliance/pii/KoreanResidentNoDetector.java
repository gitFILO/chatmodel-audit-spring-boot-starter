package io.modelaudit.chatmodel.audit.core.compliance.pii;

import java.util.regex.Pattern;

public final class KoreanResidentNoDetector implements PiiDetector {

    // 7번째 자리 [1-4]가 내국인 — 외국인(5-8)은 KoreanForeignerIdDetector
    private static final Pattern PATTERN = Pattern.compile("(?<!\\d)\\d{6}[-\\s]?[1-4]\\d{6}(?!\\d)");

    private static final String MASK = "[MASKED:rrn:01]";

    @Override
    public String id() {
        return "kr-resident-no";
    }

    @Override
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return PATTERN.matcher(input).replaceAll(MASK);
    }
}
