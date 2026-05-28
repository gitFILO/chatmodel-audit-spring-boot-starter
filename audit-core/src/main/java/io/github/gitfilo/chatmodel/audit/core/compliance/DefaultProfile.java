package io.github.gitfilo.chatmodel.audit.core.compliance;

import java.util.List;

public final class DefaultProfile implements ComplianceProfile {

    public static final DefaultProfile INSTANCE = new DefaultProfile();

    private DefaultProfile() {
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public int retentionDays() {
        return 365;
    }

    @Override
    public boolean piiMaskEnabled() {
        return false;
    }

    @Override
    public List<String> piiProviders() {
        return List.of();
    }

    @Override
    public boolean maskOutputOnSearch() {
        return false;
    }

    @Override
    public CostCurrency costCurrency() {
        return CostCurrency.USD;
    }
}
