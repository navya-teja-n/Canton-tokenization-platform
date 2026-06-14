package com.canton.platform.events.handlers;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.canton.platform.events.EventBus;
import com.canton.platform.events.RepoLifecycleEvent;
import com.canton.platform.events.Topics;

/**
 * Reacts to repo maturity events. In production this would trigger an
 * automated repurchase reminder / margin call workflow and, after a grace
 * period with no repurchase, a default-declaration job. Here it logs the
 * trigger so the event-driven flow is observable end-to-end.
 */
@Component
public class RepoMaturityEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RepoMaturityEventHandler.class);

    private final EventBus eventBus;

    public RepoMaturityEventHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void subscribe() {
        eventBus.subscribe(Topics.REPO_MATURED, event -> {
            RepoLifecycleEvent e = (RepoLifecycleEvent) event;
            log.info("[REPO-LIFECYCLE] Repo {} matured -- borrower {} should repurchase from lender {}. {}",
                    e.repoId(), e.borrower(), e.lender(), e.detail());
        });

        eventBus.subscribe(Topics.COLLATERAL_RELEASED, event -> {
            log.info("[REPO-LIFECYCLE] Collateral release event: {}", event);
        });
    }
}
