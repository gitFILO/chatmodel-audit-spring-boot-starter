package io.modelaudit.chatmodel.audit.resolver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTeamIdResolverTest {

    private final MdcTeamIdResolver resolver = new MdcTeamIdResolver("teamId");

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void resolvesFromMdc() {
        MDC.put("teamId", "platform");
        assertThat(resolver.resolve()).isEqualTo("platform");
    }

    @Test
    void returnsNullWhenAbsent() {
        assertThat(resolver.resolve()).isNull();
    }

    @Test
    void returnsNullWhenBlank() {
        MDC.put("teamId", "   ");
        assertThat(resolver.resolve()).isNull();
    }

    @Test
    void resolvesCustomKey() {
        MdcTeamIdResolver custom = new MdcTeamIdResolver("tenant");
        MDC.put("tenant", "acme");
        assertThat(custom.resolve()).isEqualTo("acme");
    }
}
