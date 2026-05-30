package io.modelaudit.chatmodel.audit.core.compliance.pii;

import java.util.regex.Pattern;

public final class KoreanResidentNoDetector implements PiiDetector {

    // 7th digit [1-4] is Korean national — foreigner (5-8) handled by KoreanForeignerIdDetector
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
