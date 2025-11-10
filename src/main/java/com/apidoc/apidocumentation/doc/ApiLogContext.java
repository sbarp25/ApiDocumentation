package com.apidoc.apidocumentation.doc;

public class ApiLogContext {
    private static final ThreadLocal<Object> requestBodyHolder = new ThreadLocal<>();

    public static void setRequestBody(Object body) {
        requestBodyHolder.set(body);
    }

    public static Object getRequestBody() {
        return requestBodyHolder.get();
    }

    public static void clear() {
        requestBodyHolder.remove();
    }
}
