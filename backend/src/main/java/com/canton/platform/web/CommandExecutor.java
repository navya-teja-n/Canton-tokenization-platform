package com.canton.platform.web;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.canton.platform.service.IdempotencyService;
import com.canton.platform.service.RetryService;

/**
 * Shared command-submission pipeline used by every mutating REST endpoint:
 * <ol>
 *   <li>{@link IdempotencyService} -- if the same {@code Idempotency-Key} was
 *       seen before with an identical request body, the cached result is
 *       returned instead of re-executing the command (prevents double
 *       settlement on client retries).</li>
 *   <li>{@link RetryService} -- the underlying ledger command is retried
 *       with exponential backoff on transient {@code LEDGER_BUSY} errors.</li>
 * </ol>
 * Mirrors how a production Ledger API client wraps
 * {@code CommandSubmissionService.SubmitAndWait} calls.
 */
@Component
public class CommandExecutor {

    private final IdempotencyService idempotencyService;
    private final RetryService retryService;

    public CommandExecutor(IdempotencyService idempotencyService, RetryService retryService) {
        this.idempotencyService = idempotencyService;
        this.retryService = retryService;
    }

    public <T> T submit(String idempotencyKey, Object requestBody, String commandDescription, Supplier<T> command) {
        return idempotencyService.execute(idempotencyKey, requestBody,
                () -> retryService.submitWithRetry(commandDescription, command));
    }
}
