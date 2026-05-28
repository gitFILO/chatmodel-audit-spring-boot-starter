package io.modelaudit.chatmodel.audit.compliance.pii;

import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanPhoneDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanPhoneDetectorTest {

    private final KoreanPhoneDetector detector = new KoreanPhoneDetector();

    @Test
    void idMatchesKrFinancialProfile() {
        assertThat(detector.id()).isEqualTo("kr-phone");
    }

    @Test
    void masksDashFormatted010Phone() {
        String result = detector.mask("연락 010-1234-5678 부탁");

        assertThat(result).isEqualTo("연락 [MASKED:tel:05] 부탁");
    }

    @Test
    void masksContinuousElevenDigits() {
        String result = detector.mask("연락 01012345678 부탁");

        assertThat(result).isEqualTo("연락 [MASKED:tel:05] 부탁");
    }

    @Test
    void masksOldFormat011() {
        String result = detector.mask("구번호 011-123-4567 보관");

        assertThat(result).isEqualTo("구번호 [MASKED:tel:05] 보관");
    }

    @Test
    void doesNotMaskGeneralLandline02() {
        String result = detector.mask("회사 02-1234-5678 안내");

        assertThat(result).isEqualTo("회사 02-1234-5678 안내");
    }

    @Test
    void nullAndEmptyReturnedAsIs() {
        assertThat(detector.mask(null)).isNull();
        assertThat(detector.mask("")).isEmpty();
    }
}
