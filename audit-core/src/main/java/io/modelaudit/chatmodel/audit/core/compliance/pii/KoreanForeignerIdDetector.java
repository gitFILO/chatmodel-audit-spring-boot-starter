package io.modelaudit.chatmodel.audit.core.compliance.pii;

import java.util.regex.Pattern;

public final class KoreanForeignerIdDetector implements PiiDetector {

    // Korean foreigner registration number: 7th digit in [5-8]
    private static final Pattern PATTERN = Pattern.compile("(?<!\\d)\\d{6}[-\\s]?[5-8]\\d{6}(?!\\d)");

    private static final String MASK = "[MASKED:fgnid:08]";

    @Override
    public String id() {
        return "kr-foreigner-id";
    }

    @Override
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return PATTERN.matcher(input).replaceAll(MASK);
    }
}
