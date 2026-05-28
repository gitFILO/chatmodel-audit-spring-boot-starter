package io.modelaudit.chatmodel.audit.resolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MdcUserIdResolverTest {

    private final MdcUserIdResolver resolver = new MdcUserIdResolver("userId");

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void resolvesFromMdc() {
        MDC.put("userId", "alice");
        assertThat(resolver.resolve()).isEqualTo("alice");
    }

    @Test
    void returnsNullWhenAbsent() {
        assertThat(resolver.resolve()).isNull();
    }

    @Test
    void returnsNullWhenBlank() {
        MDC.put("userId", "   ");
        assertThat(resolver.resolve()).isNull();
    }
}
