package com.canton.platform.events.handlers;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.canton.platform.events.EventBus;
import com.canton.platform.events.RepoLifecycleEvent;
import com.canton.platform.events.TradeExecutedEvent;
import com.canton.platform.events.Topics;

/**
 * Reacts to settlement-relevant events: trade execution and repo closure.
 * In a production system this would push notifications to downstream books
 * & records, risk, and reporting systems. Here it logs a structured
 * "settlement confirmed" message that the settlement-monitor endpoint
 * surfaces to the frontend via {@link EventBus#recentHistory(int)}.
 */
@Component
public class SettlementEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SettlementEventHandler.class);

    private final EventBus eventBus;

    public SettlementEventHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void subscribe() {
        eventBus.subscribe(Topics.TRADE_EXECUTED, event -> {
            TradeExecutedEvent e = (TradeExecutedEvent) event;
            log.info("[SETTLEMENT] Trade {} between {} and {} for {} settled (DvP complete)",
                    e.tradeId(), e.seller(), e.buyer(), e.instrumentId());
        });

        eventBus.subscribe(Topics.REPO_CLOSED, event -> {
            RepoLifecycleEvent e = (RepoLifecycleEvent) event;
            log.info("[SETTLEMENT] Repo {} between borrower={} lender={} closed: {}",
                    e.repoId(), e.borrower(), e.lender(), e.detail());
        });
    }
}
