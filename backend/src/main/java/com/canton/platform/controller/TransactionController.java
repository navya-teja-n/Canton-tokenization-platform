package com.canton.platform.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.canton.platform.events.DomainEvent;
import com.canton.platform.events.EventBus;
import com.canton.platform.ledger.CantonLedgerSimulator;
import com.canton.platform.ledger.LedgerEvent;

/**
 * Transaction history / settlement-monitor endpoints. Mirrors a Ledger API
 * {@code TransactionService.GetTransactions} subscription, scoped to the
 * witness parties of each contract event (privacy-preserving) -- and the
 * in-memory domain-event history published on the {@link EventBus} (the
 * "event-driven architecture" feed consumed by the settlement monitor UI).
 */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Ledger transaction stream and domain-event history")
public class TransactionController {

    private final CantonLedgerSimulator ledger;
    private final EventBus eventBus;

    public TransactionController(CantonLedgerSimulator ledger, EventBus eventBus) {
        this.ledger = ledger;
        this.eventBus = eventBus;
    }

    @Operation(summary = "Ledger transaction stream visible to a party (newest first)")
    @GetMapping("/party/{party}")
    public ResponseEntity<List<LedgerEvent>> forParty(@PathVariable String party) {
        return ResponseEntity.ok(ledger.getTransactionStream(party));
    }

    @Operation(summary = "Full ledger transaction stream (regulator / audit view)")
    @GetMapping("/audit")
    public ResponseEntity<List<LedgerEvent>> audit() {
        return ResponseEntity.ok(ledger.getFullTransactionStream());
    }

    @Operation(summary = "Recent domain events published on the event bus (settlement monitor feed)")
    @GetMapping("/events/recent")
    public ResponseEntity<List<DomainEvent>> recentEvents(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(eventBus.recentHistory(limit));
    }
}
