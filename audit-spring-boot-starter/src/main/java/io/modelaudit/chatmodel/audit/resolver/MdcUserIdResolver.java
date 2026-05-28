package io.modelaudit.chatmodel.audit.resolver;

import io.modelaudit.chatmodel.audit.core.resolver.UserIdResolver;
import org.slf4j.MDC;

public final class MdcUserIdResolver implements UserIdResolver {

    private final String mdcKey;

    public MdcUserIdResolver(String mdcKey) {
        this.mdcKey = mdcKey;
    }

    @Override
    public String resolve() {
        String value = MDC.get(mdcKey);
        return (value == null || value.isBlank()) ? null : value;
    }
}
