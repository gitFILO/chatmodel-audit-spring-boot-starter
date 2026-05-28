package io.modelaudit.chatmodel.audit.resolver;

import io.modelaudit.chatmodel.audit.core.resolver.UserIdResolver;

import java.lang.reflect.Method;

// Spring Security가 클래스패스에 없으면 SecurityContextHolder 로딩 실패 — 그 경우 null 반환
public final class SpringSecurityUserIdResolver implements UserIdResolver {

    private static final String ANONYMOUS = "anonymousUser";

    private final Method getContext;
    private final Method getAuthentication;
    private final Method isAuthenticated;
    private final Method getName;

    public SpringSecurityUserIdResolver() {
        Method ctx = null;
        Method auth = null;
        Method authenticated = null;
        Method name = null;
        try {
            Class<?> holder = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Class<?> context = Class.forName("org.springframework.security.core.context.SecurityContext");
            Class<?> authentication = Class.forName("org.springframework.security.core.Authentication");
            ctx = holder.getMethod("getContext");
            auth = context.getMethod("getAuthentication");
            authenticated = authentication.getMethod("isAuthenticated");
            name = authentication.getMethod("getName");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }
        this.getContext = ctx;
        this.getAuthentication = auth;
        this.isAuthenticated = authenticated;
        this.getName = name;
    }

    @Override
    public String resolve() {
        if (getContext == null) {
            return null;
        }
        try {
            Object context = getContext.invoke(null);
            if (context == null) {
                return null;
            }
            Object authentication = getAuthentication.invoke(context);
            if (authentication == null) {
                return null;
            }
            Boolean authenticated = (Boolean) isAuthenticated.invoke(authentication);
            if (!Boolean.TRUE.equals(authenticated)) {
                return null;
            }
            Object value = getName.invoke(authentication);
            if (value == null) {
                return null;
            }
            String userId = value.toString();
            return (userId.isBlank() || ANONYMOUS.equals(userId)) ? null : userId;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
