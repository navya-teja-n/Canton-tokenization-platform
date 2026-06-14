package com.canton.platform.service;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.canton.platform.config.PlatformProperties;
import com.canton.platform.exception.IdempotencyConflictException;

/**
 * Provides idempotent command submission semantics for the REST layer:
 * if a request is retried with the same {@code Idempotency-Key} and an
 * identical request body, the cached result of the first execution is
 * returned rather than re-executing the (non-idempotent) ledger command.
 * If the same key is reused with a *different* body, the request is
 * rejected -- this prevents silent double-settlement on client retries,
 * a critical property for financial command submission.
 */
@Service
public class IdempotencyService {

    private record Entry(int requestHash, Object response, Instant createdAt) {}

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final PlatformProperties properties;

    public IdempotencyService(PlatformProperties properties) {
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public <T> T execute(String idempotencyKey, Object requestPayload, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        evictExpired();
        int requestHash = Objects.hashCode(requestPayload);

        Entry existing = store.get(idempotencyKey);
        if (existing != null) {
            if (existing.requestHash() != requestHash) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return (T) existing.response();
        }

        T result = action.get();
        store.put(idempotencyKey, new Entry(requestHash, result, Instant.now()));
        return result;
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minusSeconds(properties.getIdempotency().getTtlSeconds());
        store.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
