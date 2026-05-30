package io.modelaudit.chatmodel.audit.resolver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityUserIdResolverTest {

    // spring-security absent from classpath — always returns null
    @Test
    void returnsNullWithoutSpringSecurityOnClasspath() {
        SpringSecurityUserIdResolver resolver = new SpringSecurityUserIdResolver();
        assertThat(resolver.resolve()).isNull();
    }
}
