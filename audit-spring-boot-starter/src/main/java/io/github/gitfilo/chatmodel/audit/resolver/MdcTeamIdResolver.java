package io.github.gitfilo.chatmodel.audit.resolver;

import io.github.gitfilo.chatmodel.audit.core.resolver.TeamIdResolver;
import org.slf4j.MDC;

public final class MdcTeamIdResolver implements TeamIdResolver {

    private final String mdcKey;

    public MdcTeamIdResolver(String mdcKey) {
        this.mdcKey = mdcKey;
    }

    @Override
    public String resolve() {
        String value = MDC.get(mdcKey);
        return (value == null || value.isBlank()) ? null : value;
    }
}
