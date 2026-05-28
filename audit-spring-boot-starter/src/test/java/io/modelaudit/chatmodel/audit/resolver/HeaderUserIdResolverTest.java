package io.modelaudit.chatmodel.audit.resolver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderUserIdResolverTest {

    // spring-web/servlet-api 미존재 — 항상 null
    @Test
    void returnsNullWithoutSpringWebOnClasspath() {
        HeaderUserIdResolver resolver = new HeaderUserIdResolver("X-User-Id");
        assertThat(resolver.resolve()).isNull();
    }
}
