package io.modelaudit.chatmodel.audit.compliance.pii;

import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanForeignerIdDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanForeignerIdDetectorTest {

    private final KoreanForeignerIdDetector detector = new KoreanForeignerIdDetector();

    @Test
    void idMatchesKrFinancialProfile() {
        assertThat(detector.id()).isEqualTo("kr-foreigner-id");
    }

    @Test
    void masksForeignerSeventhDigitFiveThroughEight() {
        String result = detector.mask("외국인 900101-5234567 확인 후 900101-8234567 도");

        assertThat(result).isEqualTo("외국인 [MASKED:fgnid:08] 확인 후 [MASKED:fgnid:08] 도");
    }

    @Test
    void skipsResidentSeventhDigit() {
        // Korean nationals [1-4] are ignored
        String result = detector.mask("내국인 900101-1234567 미적용");

        assertThat(result).isEqualTo("내국인 900101-1234567 미적용");
    }

    @Test
    void nullAndEmptyReturnedAsIs() {
        assertThat(detector.mask(null)).isNull();
        assertThat(detector.mask("")).isEmpty();
    }
}
