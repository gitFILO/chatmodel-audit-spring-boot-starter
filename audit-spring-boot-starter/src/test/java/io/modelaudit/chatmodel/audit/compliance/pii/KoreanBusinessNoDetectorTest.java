package io.modelaudit.chatmodel.audit.compliance.pii;

import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanBusinessNoDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanBusinessNoDetectorTest {

    private final KoreanBusinessNoDetector detector = new KoreanBusinessNoDetector();

    @Test
    void idMatchesKrFinancialProfile() {
        assertThat(detector.id()).isEqualTo("kr-business-no");
    }

    @Test
    void masksDashFormattedBusinessNumber() {
        String result = detector.mask("사업자 123-45-67890 등록");

        assertThat(result).isEqualTo("사업자 [MASKED:bizno:04] 등록");
    }

    @Test
    void masksContinuousTenDigits() {
        String result = detector.mask("BIZNO 1234567890 입력");

        assertThat(result).isEqualTo("BIZNO [MASKED:bizno:04] 입력");
    }

    @Test
    void doesNotMaskNineDigitSubstring() {
        String result = detector.mask("번호 123456789 표시");

        assertThat(result).isEqualTo("번호 123456789 표시");
    }

    @Test
    void nullAndEmptyReturnedAsIs() {
        assertThat(detector.mask(null)).isNull();
        assertThat(detector.mask("")).isEmpty();
    }
}
