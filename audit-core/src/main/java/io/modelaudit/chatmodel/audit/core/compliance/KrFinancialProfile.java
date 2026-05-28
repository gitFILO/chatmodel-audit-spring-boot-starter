package io.modelaudit.chatmodel.audit.core.compliance;

import java.util.List;

public final class KrFinancialProfile implements ComplianceProfile {

    public static final KrFinancialProfile INSTANCE = new KrFinancialProfile();

    // vault 06-korean-compliance §3 — 한국 금융 PII 8종 표준 셋
    private static final List<String> PII_PROVIDERS = List.of(
            "resident-no",
            "account",
            "card",
            "business-no",
            "phone",
            "email",
            "health-insurance-no",
            "foreigner-id"
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
