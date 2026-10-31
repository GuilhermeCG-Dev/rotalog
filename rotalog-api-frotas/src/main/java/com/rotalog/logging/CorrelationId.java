package com.rotalog.logging;

/**
 * Chave do correlation-id no MDC e nome do header HTTP repassado às demais APIs.
 */
public final class CorrelationId {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-ID";

    private CorrelationId() {
    }
}
