package com.canton.platform.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Request to mark a repo as matured once {@code asOfDate >= maturityDate}. Mirrors {@code MarkMatured}. */
public record RepoMatureRequest(
        @NotNull LocalDate asOfDate,
        String correlationId
) {}
