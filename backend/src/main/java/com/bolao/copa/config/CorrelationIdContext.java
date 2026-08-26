package com.bolao.copa.config;

public final class CorrelationIdContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationIdContext() {
    }

    public static String get() { return CURRENT.get(); }
    static void set(String value) { CURRENT.set(value); }
    static void clear() { CURRENT.remove(); }
}
