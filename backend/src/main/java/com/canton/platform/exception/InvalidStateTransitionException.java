package com.canton.platform.exception;

import org.springframework.http.HttpStatus;

/** Mirrors a DAML {@code ensure}/{@code assertMsg} failure on a choice. */
public class InvalidStateTransitionException extends PlatformException {
    public InvalidStateTransitionException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", message);
    }
}
