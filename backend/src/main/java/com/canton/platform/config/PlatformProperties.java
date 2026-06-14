package com.canton.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/** Strongly-typed binding for the {@code platform.*} configuration block. */
@Configuration
@ConfigurationProperties(prefix = "platform")
@Data
public class PlatformProperties {

    private Ledger ledger = new Ledger();
    private Retry retry = new Retry();
    private Idempotency idempotency = new Idempotency();

    @Data
    public static class Ledger {
        private String participantId = "participant1";
        private String ledgerId = "canton-sim-ledger";
    }

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private long initialBackoffMs = 200;
        private double multiplier = 2.0;
    }

    @Data
    public static class Idempotency {
        private long ttlSeconds = 3600;
    }
}
