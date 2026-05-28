package io.modelaudit.chatmodel.audit.compliance;

import io.modelaudit.chatmodel.audit.core.compliance.ComplianceProfile;
import io.modelaudit.chatmodel.audit.core.compliance.CostCurrency;
import io.modelaudit.chatmodel.audit.core.compliance.DefaultProfile;
import io.modelaudit.chatmodel.audit.core.compliance.KrFinancialProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComplianceProfileTest {

    @Test
    void defaultProfileDeclaresOneYearUsdNoPii() {
        ComplianceProfile p = DefaultProfile.INSTANCE;

        assertThat(p.name()).isEqualTo("default");
        assertThat(p.retentionDays()).isEqualTo(365);
        assertThat(p.piiMaskEnabled()).isFalse();
        assertThat(p.piiProviders()).isEmpty();
        assertThat(p.maskOutputOnSearch()).isFalse();
        assertThat(p.costCurrency()).isEqualTo(CostCurrency.USD);
    }

    @Test
    void krFinancialDeclaresFiveYearKrwWithSixV01Detectors() {
        ComplianceProfile p = KrFinancialProfile.INSTANCE;

        assertThat(p.name()).isEqualTo("kr-financial");
        assertThat(p.retentionDays()).isEqualTo(1825);
        assertThat(p.piiMaskEnabled()).isTrue();
        assertThat(p.maskOutputOnSearch()).isTrue();
        assertThat(p.costCurrency()).isEqualTo(CostCurrency.KRW);
        // PiiDetector.id() 매칭 — v0.1 한국 6종 + 국가 중립 email
        assertThat(p.piiProviders()).containsExactly(
                "kr-resident-no",
                "kr-foreigner-id",
                "kr-card",
                "kr-business-no",
                "kr-phone",
                "email"
        );
    }
}
