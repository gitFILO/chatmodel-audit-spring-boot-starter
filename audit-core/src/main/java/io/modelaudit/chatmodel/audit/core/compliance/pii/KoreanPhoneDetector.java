package io.modelaudit.chatmodel.audit.core.compliance.pii;

import java.util.regex.Pattern;

public final class KoreanPhoneDetector implements PiiDetector {

    // 010/011/016/017/018/019 mobile (current 010 standard + legacy prefixes)
    private static final Pattern PATTERN = Pattern.compile(
            "(?<!\\d)01\\d[-\\s]?\\d{3,4}[-\\s]?\\d{4}(?!\\d)");

    private static final String MASK = "[MASKED:tel:05]";

    @Override
    public String id() {
        return "kr-phone";
    }

    @Override
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return PATTERN.matcher(input).replaceAll(MASK);
    }
}
