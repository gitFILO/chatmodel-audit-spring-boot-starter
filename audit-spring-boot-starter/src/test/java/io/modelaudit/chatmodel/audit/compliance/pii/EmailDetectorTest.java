package io.modelaudit.chatmodel.audit.compliance.pii;

import io.modelaudit.chatmodel.audit.core.compliance.pii.EmailDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDetectorTest {

    private final EmailDetector detector = new EmailDetector();

    @Test
    void idIsCountryNeutral() {
        assertThat(detector.id()).isEqualTo("email");
    }

    @Test
    void masksStandardEmail() {
        String result = detector.mask("연락처 user@example.com 참조");

        assertThat(result).isEqualTo("연락처 [MASKED:email:06] 참조");
    }

    @Test
    void masksMultipleEmails() {
        String result = detector.mask("a.b+tag@corp.co.kr 와 admin@test.io");

        assertThat(result).isEqualTo("[MASKED:email:06] 와 [MASKED:email:06]");
    }

    @Test
    void doesNotMaskAtSymbolWithoutDomain() {
        String result = detector.mask("그냥 @mention 만");

        assertThat(result).isEqualTo("그냥 @mention 만");
    }

    @Test
    void nullAndEmptyReturnedAsIs() {
        assertThat(detector.mask(null)).isNull();
        assertThat(detector.mask("")).isEmpty();
    }
}
