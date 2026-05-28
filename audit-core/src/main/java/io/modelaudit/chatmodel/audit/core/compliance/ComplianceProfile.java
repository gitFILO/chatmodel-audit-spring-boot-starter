package io.modelaudit.chatmodel.audit.core.compliance;

import java.util.List;

// 외부 starter가 자기 ComplianceProfile만 @Bean 등록하면 mode 한 줄로 활성화 — 빈 선택은 AutoConfig의 Map<String,ComplianceProfile> lookup
public interface ComplianceProfile {

    String name();

    int retentionDays();

    boolean piiMaskEnabled();

    List<String> piiProviders();

    boolean maskOutputOnSearch();

    CostCurrency costCurrency();
}
