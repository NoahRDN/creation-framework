package edu.noah.monframework.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Stores the current request/response in a ThreadLocal during dispatch.
 * <p>
 * This allows framework utilities (e.g. security checks) to access request context
 * even when methods are invoked indirectly from other methods.
 */
public final class RequestContextHolder {
    private RequestContextHolder() {}

    private static final ThreadLocal<Context> CTX = new ThreadLocal<>();

    public static void set(HttpServletRequest request, HttpServletResponse response) {
        if (request == null || response == null) {
            CTX.remove();
            return;
        }
        CTX.set(new Context(request, response));
    }

    public static HttpServletRequest request() {
        Context ctx = CTX.get();
        return ctx != null ? ctx.request : null;
    }

    public static HttpServletResponse response() {
        Context ctx = CTX.get();
        return ctx != null ? ctx.response : null;
    }

    public static void clear() {
        CTX.remove();
    }

    private record Context(HttpServletRequest request, HttpServletResponse response) {}
}

