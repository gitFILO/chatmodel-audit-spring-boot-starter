package io.modelaudit.chatmodel.audit.resolver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityUserIdResolverTest {

    // spring-security가 클래스패스에 없는 환경 — 항상 null 반환
    @Test
    void returnsNullWithoutSpringSecurityOnClasspath() {
        SpringSecurityUserIdResolver resolver = new SpringSecurityUserIdResolver();
        assertThat(resolver.resolve()).isNull();
    }
}
