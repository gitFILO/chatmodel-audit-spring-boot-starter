package io.modelaudit.chatmodel.audit.resolver;

import io.modelaudit.chatmodel.audit.core.resolver.UserIdResolver;

import java.lang.reflect.Method;

// spring-web가 클래스패스에 없으면 RequestContextHolder 로딩 실패 — 그 경우 null 반환
public final class HeaderUserIdResolver implements UserIdResolver {

    private final String headerName;
    private final Method getRequestAttributes;
    private final Method getRequest;
    private final Method getHeader;

    public HeaderUserIdResolver(String headerName) {
        this.headerName = headerName;
        Method attrs = null;
        Method request = null;
        Method header = null;
        try {
            Class<?> holder = Class.forName("org.springframework.web.context.request.RequestContextHolder");
            Class<?> servletAttrs = Class.forName("org.springframework.web.context.request.ServletRequestAttributes");
            Class<?> httpRequest = Class.forName("jakarta.servlet.http.HttpServletRequest");
            attrs = holder.getMethod("getRequestAttributes");
            request = servletAttrs.getMethod("getRequest");
            header = httpRequest.getMethod("getHeader", String.class);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }
        this.getRequestAttributes = attrs;
        this.getRequest = request;
        this.getHeader = header;
    }

    @Override
    public String resolve() {
        if (getRequestAttributes == null) {
            return null;
        }
        try {
            Object attrs = getRequestAttributes.invoke(null);
            if (attrs == null || !getRequest.getDeclaringClass().isInstance(attrs)) {
                return null;
            }
            Object request = getRequest.invoke(attrs);
            if (request == null) {
                return null;
            }
            Object value = getHeader.invoke(request, headerName);
            if (value == null) {
                return null;
            }
            String userId = value.toString();
            return userId.isBlank() ? null : userId;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
