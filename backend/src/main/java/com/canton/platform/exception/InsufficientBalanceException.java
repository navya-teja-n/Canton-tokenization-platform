package com.canton.platform.exception;

import org.springframework.http.HttpStatus;

public class InsufficientBalanceException extends PlatformException {
    public InsufficientBalanceException(String message) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", message);
    }
}
