package io.modelaudit.chatmodel.audit.writer;

public enum OverflowPolicy {
    BLOCK,
    DROP;

    public static OverflowPolicy from(String value) {
        if (value == null) {
            return BLOCK;
        }
        return switch (value.toLowerCase()) {
            case "drop" -> DROP;
            default -> BLOCK;
        };
    }
}
