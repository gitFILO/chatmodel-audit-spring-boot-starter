package io.modelaudit.chatmodel.audit.compliance.pii;

import io.modelaudit.chatmodel.audit.core.compliance.KrFinancialProfile;
import io.modelaudit.chatmodel.audit.core.compliance.pii.EmailDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanBusinessNoDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanCardDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanForeignerIdDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanPhoneDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanResidentNoDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.PiiDetector;
import io.modelaudit.chatmodel.audit.core.compliance.pii.PiiMaskService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskServiceTest {

    private final List<PiiDetector> krDetectors = List.of(
            new KoreanResidentNoDetector(),
            new KoreanForeignerIdDetector(),
            new KoreanCardDetector(),
            new KoreanBusinessNoDetector(),
            new KoreanPhoneDetector(),
            new EmailDetector()
    );

    @Test
    void registryExposesEveryProvidedDetectorById() {
        PiiMaskService svc = new PiiMaskService(krDetectors, KrFinancialProfile.INSTANCE.piiProviders());

        assertThat(svc.registry().keySet())
                .containsExactlyInAnyOrder("kr-resident-no", "kr-foreigner-id", "kr-card",
                        "kr-business-no", "kr-phone", "email");
    }

    @Test
    void krFinancialProfileIdsAllResolveToRegisteredDetectors() {
        PiiMaskService svc = new PiiMaskService(krDetectors, KrFinancialProfile.INSTANCE.piiProviders());

        assertThat(svc.activeIds()).isEqualTo(KrFinancialProfile.INSTANCE.piiProviders());
        for (String id : svc.activeIds()) {
            assertThat(svc.registry()).containsKey(id);
        }
    }

    @Test
    void appliesActiveDetectorsInDeclaredOrder() {
        PiiMaskService svc = new PiiMaskService(krDetectors, KrFinancialProfile.INSTANCE.piiProviders());

        String input = "박지훈(900101-1234567) 카드 1234-5678-9012-3456 연락 010-1111-2222 mail user@x.com";
        String masked = svc.mask(input);

        assertThat(masked).contains("[MASKED:rrn:01]");
        assertThat(masked).contains("[MASKED:card:03]");
        assertThat(masked).contains("[MASKED:tel:05]");
        assertThat(masked).contains("[MASKED:email:06]");
        assertThat(masked).doesNotContain("900101-1234567");
        assertThat(masked).doesNotContain("1234-5678-9012-3456");
        assertThat(masked).doesNotContain("010-1111-2222");
        assertThat(masked).doesNotContain("user@x.com");
    }

    @Test
    void skipsDetectorsNotInActiveIds() {
        // Only email active — phone is registered but not applied
        PiiMaskService svc = new PiiMaskService(krDetectors, List.of("email"));

        String masked = svc.mask("연락 010-1234-5678 메일 a@b.com");

        assertThat(masked).isEqualTo("연락 010-1234-5678 메일 [MASKED:email:06]");
    }

    @Test
    void unknownActiveIdSilentlyIgnored() {
        PiiMaskService svc = new PiiMaskService(krDetectors, List.of("kr-unknown", "email"));

        String masked = svc.mask("mail a@b.com");

        assertThat(masked).isEqualTo("mail [MASKED:email:06]");
    }

    @Test
    void emptyActiveIdsReturnsInputUnchanged() {
        PiiMaskService svc = new PiiMaskService(krDetectors, List.of());

        assertThat(svc.mask("주민 900101-1234567 그대로")).isEqualTo("주민 900101-1234567 그대로");
    }

    @Test
    void nullAndEmptyInputReturnedAsIs() {
        PiiMaskService svc = new PiiMaskService(krDetectors, KrFinancialProfile.INSTANCE.piiProviders());

        assertThat(svc.mask(null)).isNull();
        assertThat(svc.mask("")).isEmpty();
    }

    @Test
    void externalStarterDetectorIsDiscovered() {
        // Simulates a detector @Bean registered by an external starter (audit-us-financial-starter)
        PiiDetector ssn = new PiiDetector() {
            @Override public String id() { return "us-ssn"; }
            @Override public String mask(String input) {
                return input.replaceAll("\\d{3}-\\d{2}-\\d{4}", "[MASKED:ssn:US]");
            }
        };
        List<PiiDetector> combined = List.of(new EmailDetector(), ssn);

        PiiMaskService svc = new PiiMaskService(combined, List.of("us-ssn", "email"));

        String masked = svc.mask("SSN 123-45-6789 mail a@b.com");

        assertThat(masked).isEqualTo("SSN [MASKED:ssn:US] mail [MASKED:email:06]");
    }
}
