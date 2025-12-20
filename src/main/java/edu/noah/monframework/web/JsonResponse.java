package edu.noah.monframework.web;

import java.util.Collections;
import java.util.Map;

/**
 * Simple wrapper to let handlers choose the HTTP status and optional metadata
 * when returning JSON via @ConvertJson.
 */
public final class JsonResponse {
    private final int status;
    private final Object body;
    private final Map<String, Object> meta;

    public JsonResponse(int status, Object body) {
        this(status, body, null);
    }

    public JsonResponse(int status, Object body, Map<String, Object> meta) {
        this.status = status;
        this.body = body;
        this.meta = (meta == null ? null : Collections.unmodifiableMap(meta));
    }

    public int status() {
        return status;
    }

    public Object body() {
        return body;
    }

    public Map<String, Object> meta() {
        return meta;
    }

    public static JsonResponse of(int status, Object body) {
        return new JsonResponse(status, body);
    }

    public static JsonResponse of(int status, Object body, Map<String, Object> meta) {
        return new JsonResponse(status, body, meta);
    }

    public static JsonResponse ok(Object body) {
        return of(200, body);
    }

    public static JsonResponse ok(Object body, Map<String, Object> meta) {
        return of(200, body, meta);
    }
}
