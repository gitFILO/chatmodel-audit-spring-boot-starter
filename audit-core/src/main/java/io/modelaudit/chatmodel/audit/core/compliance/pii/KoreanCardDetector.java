package io.modelaudit.chatmodel.audit.core.compliance.pii;

import java.util.regex.Pattern;

public final class KoreanCardDetector implements PiiDetector {

    // 4-4-4-4 카드 패턴 — 구분자는 dash/space/없음 혼용
    private static final Pattern PATTERN = Pattern.compile(
            "(?<!\\d)\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)");

    private static final String MASK = "[MASKED:card:03]";

    @Override
    public String id() {
        return "kr-card";
    }

    @Override
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return PATTERN.matcher(input).replaceAll(MASK);
    }
}
