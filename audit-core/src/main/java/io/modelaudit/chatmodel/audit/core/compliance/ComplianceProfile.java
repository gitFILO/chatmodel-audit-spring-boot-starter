package io.modelaudit.chatmodel.audit.core.compliance;

import java.util.List;

public interface ComplianceProfile {

    String name();

    int retentionDays();

    boolean piiMaskEnabled();

    List<String> piiProviders();

    boolean maskOutputOnSearch();

    CostCurrency costCurrency();

    // audit.compliance.compliance.mode 값을 받아 프로파일 인스턴스를 선택 — null/미지원 모드는 DefaultProfile
    static ComplianceProfile fromMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return DefaultProfile.INSTANCE;
        }
        return switch (mode) {
            case "kr-financial" -> KrFinancialProfile.INSTANCE;
            default -> DefaultProfile.INSTANCE;
        };
    }
}
