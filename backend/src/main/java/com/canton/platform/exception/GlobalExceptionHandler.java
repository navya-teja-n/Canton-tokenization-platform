package com.canton.platform.exception;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.canton.platform.ledger.LedgerException;

/**
 * Centralized error handling: maps business exceptions (mirroring DAML
 * {@code ensure}/{@code assertMsg}/authorization failures), ledger
 * exceptions, and request-validation failures to structured JSON error
 * responses with appropriate HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ErrorResponse> handlePlatformException(PlatformException ex, WebRequest request) {
        log.warn("Platform exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(new ErrorResponse(
                Instant.now(), ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(),
                path(request), List.of()));
    }

    @ExceptionHandler(LedgerException.class)
    public ResponseEntity<ErrorResponse> handleLedgerException(LedgerException ex, WebRequest request) {
        log.warn("Ledger exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        HttpStatus status = switch (ex.getErrorCode()) {
            case "CONTRACT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "NOT_AUTHORIZED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(), status.value(), ex.getErrorCode(), ex.getMessage(), path(request), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(
                Instant.now(), HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "Request validation failed", path(request), details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
                Instant.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "An unexpected error occurred", path(request), List.of()));
    }

    private String path(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
