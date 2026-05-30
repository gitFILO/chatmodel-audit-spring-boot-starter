package io.modelaudit.chatmodel.audit.core.compliance;

import java.util.List;

public final class KrFinancialProfile implements ComplianceProfile {

    public static final KrFinancialProfile INSTANCE = new KrFinancialProfile();

    // v0.1 ships 6 Korean PII types — ids match PiiDetector.id() (kr-account/kr-health-insurance-no extend via external detectors)
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

    // Electronic Financial Supervisory Regulation + KR financial sector 5-year retention standard
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
