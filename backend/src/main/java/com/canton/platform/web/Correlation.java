package com.canton.platform.web;

import java.util.UUID;

/** Small helper for correlation-id propagation through commands and events. */
public final class Correlation {
    private Correlation() {}

    /** Returns the supplied correlation id, or generates a new one if blank/absent. */
    public static String idOrNew(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }
}
