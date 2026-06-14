package com.canton.platform.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends PlatformException {
    public IdempotencyConflictException(String idempotencyKey) {
        super(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                "Idempotency-Key '" + idempotencyKey + "' was already used with a different request body");
    }
}
