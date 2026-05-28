package io.modelaudit.chatmodel.audit.compliance.pii;

import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanCardDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanCardDetectorTest {

    private final KoreanCardDetector detector = new KoreanCardDetector();

    @Test
    void idMatchesKrFinancialProfile() {
        assertThat(detector.id()).isEqualTo("kr-card");
    }

    @Test
    void masksDashSeparatedCard() {
        String result = detector.mask("카드 1234-5678-9012-3456 결제");

        assertThat(result).isEqualTo("카드 [MASKED:card:03] 결제");
    }

    @Test
    void masksContinuousSixteenDigits() {
        String result = detector.mask("카드 1234567890123456 결제");

        assertThat(result).isEqualTo("카드 [MASKED:card:03] 결제");
    }

    @Test
    void doesNotMaskFifteenDigitSubstring() {
        // 15자리는 카드 패턴 미충족
        String result = detector.mask("번호 123456789012345 표시");

        assertThat(result).isEqualTo("번호 123456789012345 표시");
    }

    @Test
    void nullAndEmptyReturnedAsIs() {
        assertThat(detector.mask(null)).isNull();
        assertThat(detector.mask("")).isEmpty();
    }
}
