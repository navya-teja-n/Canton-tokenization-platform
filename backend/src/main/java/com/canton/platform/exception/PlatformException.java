package com.canton.platform.exception;

import org.springframework.http.HttpStatus;

/** Base type for all business-rule exceptions thrown by the service layer. */
public class PlatformException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public PlatformException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
}
