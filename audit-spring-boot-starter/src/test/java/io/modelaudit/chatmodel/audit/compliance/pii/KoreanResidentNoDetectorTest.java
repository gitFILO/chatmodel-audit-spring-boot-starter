package io.modelaudit.chatmodel.audit.compliance.pii;

import io.modelaudit.chatmodel.audit.core.compliance.pii.KoreanResidentNoDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanResidentNoDetectorTest {

    private final KoreanResidentNoDetector detector = new KoreanResidentNoDetector();

    @Test
    void idMatchesKrFinancialProfile() {
        assertThat(detector.id()).isEqualTo("kr-resident-no");
    }

    @Test
    void masksDashedKoreanResidentNumber() {
        String result = detector.mask("박지훈(900101-1234567)님 안녕하세요");

        assertThat(result).isEqualTo("박지훈([MASKED:rrn:01])님 안녕하세요");
    }

    @Test
    void masksContinuousResidentNumberWithoutDash() {
        String result = detector.mask("주민번호 9001011234567 확인");

        assertThat(result).isEqualTo("주민번호 [MASKED:rrn:01] 확인");
    }

    @Test
    void skipsForeignerSeventhDigit() {
        // Foreigner [5-8] -> resident detector does not apply
        String result = detector.mask("외국인 900101-5234567 미적용");

        assertThat(result).isEqualTo("외국인 900101-5234567 미적용");
    }

    @Test
    void nullAndEmptyReturnedAsIs() {
        assertThat(detector.mask(null)).isNull();
        assertThat(detector.mask("")).isEmpty();
    }
}
