package com.canton.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Institutional Tokenization Engine + Blockchain-Based
 * Deposit Network backend.
 *
 * This service integrates with a (simulated) Canton/DAML Ledger API: see
 * {@code com.canton.platform.ledger.CantonLedgerSimulator} for the in-memory
 * Active Contract Set and transaction stream that mirrors the DAML
 * templates defined under {@code /daml}.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class PlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
