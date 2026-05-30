package io.modelaudit.chatmodel.audit.core.compliance;

import java.util.List;

// External starter registers its own ComplianceProfile @Bean and activates via a single mode line — AutoConfig selects via Map<String,ComplianceProfile> lookup
public interface ComplianceProfile {

    String name();

    int retentionDays();

    boolean piiMaskEnabled();

    List<String> piiProviders();

    boolean maskOutputOnSearch();

    CostCurrency costCurrency();
}
