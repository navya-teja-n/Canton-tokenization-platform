package com.canton.platform.service;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.canton.platform.config.PlatformProperties;
import com.canton.platform.ledger.LedgerException;

/**
 * Wraps ledger command submission with retry + exponential backoff,
 * mirroring how a production Ledger API client would handle transient
 * gRPC errors (e.g. {@code UNAVAILABLE}, {@code ABORTED} due to contention
 * on the Active Contract Set). Only transient errors are retried;
 * business-rule failures ({@code LedgerException} with a non-transient
 * code) fail fast.
 */
@Service
public class RetryService {

    private static final Logger log = LoggerFactory.getLogger(RetryService.class);

    private final PlatformProperties properties;

    public RetryService(PlatformProperties properties) {
        this.properties = properties;
    }

    public <T> T submitWithRetry(String commandDescription, Supplier<T> command) {
        int maxAttempts = properties.getRetry().getMaxAttempts();
        long backoff = properties.getRetry().getInitialBackoffMs();
        double multiplier = properties.getRetry().getMultiplier();

        PlatformRuntimeError lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return command.get();
            } catch (LedgerException ex) {
                if (!isTransient(ex)) {
                    throw ex;
                }
                log.warn("Transient ledger error on attempt {}/{} for '{}': {}",
                        attempt, maxAttempts, commandDescription, ex.getMessage());
                lastError = new PlatformRuntimeError(ex);
                sleep(backoff);
                backoff = (long) (backoff * multiplier);
            }
        }
        throw lastError != null ? lastError.cause : new IllegalStateException("Retry exhausted");
    }

    private boolean isTransient(LedgerException ex) {
        return "LEDGER_BUSY".equals(ex.getErrorCode());
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class PlatformRuntimeError extends RuntimeException {
        final LedgerException cause;
        PlatformRuntimeError(LedgerException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
