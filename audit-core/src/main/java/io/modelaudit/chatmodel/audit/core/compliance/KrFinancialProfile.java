package io.modelaudit.chatmodel.audit.core.compliance;

import java.util.List;

public final class KrFinancialProfile implements ComplianceProfile {

    public static final KrFinancialProfile INSTANCE = new KrFinancialProfile();

    // v0.1 한국 PII 6종 — id는 PiiDetector.id()와 매칭 (kr-account/kr-health-insurance-no는 외부 detector로 확장)
    private static final List<String> PII_PROVIDERS = List.of(
            "kr-resident-no",
            "kr-foreigner-id",
            "kr-card",
            "kr-business-no",
            "kr-phone",
            "email"
    );

    private KrFinancialProfile() {
    }

    @Override
    public String name() {
        return "kr-financial";
    }

    // 전자금융감독규정 + 금융권 5년 보관 표준
    @Override
    public int retentionDays() {
        return 1825;
    }

    @Override
    public boolean piiMaskEnabled() {
        return true;
    }

    @Override
    public List<String> piiProviders() {
        return PII_PROVIDERS;
    }

    @Override
    public boolean maskOutputOnSearch() {
        return true;
    }

    @Override
    public CostCurrency costCurrency() {
        return CostCurrency.KRW;
    }
}
