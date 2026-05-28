package io.modelaudit.chatmodel.audit.core.compliance.pii;

import java.util.regex.Pattern;

public final class EmailDetector implements PiiDetector {

    // RFC 5322 단순화 — local@domain.tld
    private static final Pattern PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final String MASK = "[MASKED:email:06]";

    @Override
    public String id() {
        return "email";
    }

    @Override
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return PATTERN.matcher(input).replaceAll(MASK);
    }
}
