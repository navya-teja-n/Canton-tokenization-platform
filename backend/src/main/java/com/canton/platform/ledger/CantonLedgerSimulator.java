package com.canton.platform.ledger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory simulation of a Canton participant node's Ledger API:
 * <ul>
 *   <li>Maintains an Active Contract Set (ACS) keyed by contract id</li>
 *   <li>Appends CREATE/ARCHIVE events to a transaction log (event stream)</li>
 *   <li>Enforces template-typed reads and basic visibility (witnesses)</li>
 * </ul>
 *
 * Each "command" against this simulator corresponds to one or more
 * create/archive operations executed atomically (within a single
 * {@code synchronized} block), mirroring the atomicity of a single DAML
 * transaction (e.g. a DvP settlement that transfers an asset AND pays
 * cash in one step).
 *
 * In a production deployment this class would be replaced by a gRPC client
 * to the Canton Ledger API (Command Submission Service + Transaction
 * Service); see {@code docs/LEDGER_INTEGRATION.md}.
 */
@Component
public class CantonLedgerSimulator {

    private static final Logger log = LoggerFactory.getLogger(CantonLedgerSimulator.class);

    private final Map<String, LedgerContract<?>> activeContracts = new ConcurrentHashMap<>();
    private final List<LedgerEvent> transactionLog = new CopyOnWriteArrayList<>();
    private final AtomicLong contractSeq = new AtomicLong(0);
    private final AtomicLong eventSeq = new AtomicLong(0);
    private final Object commitLock = new Object();

    /**
     * Create a new active contract. Returns the assigned contract id, in
     * the style {@code 00<seq>:<templateId>} approximating a Canton
     * contract id.
     */
    public <T> String create(String templateId, T payload, Set<String> signatories, Set<String> observers) {
        synchronized (commitLock) {
            String contractId = nextContractId(templateId);
            LedgerContract<T> contract = new LedgerContract<>(
                    contractId, templateId, payload, Set.copyOf(signatories), Set.copyOf(observers), Instant.now());
            activeContracts.put(contractId, contract);
            recordEvent(LedgerEvent.EventType.CREATED, templateId, contractId, payload, contract.witnesses());
            log.info("Ledger CREATE {} contractId={} signatories={} observers={}",
                    templateId, contractId, signatories, observers);
            return contractId;
        }
    }

    /** Archive (consume) an active contract. */
    public void archive(String contractId) {
        synchronized (commitLock) {
            LedgerContract<?> contract = activeContracts.remove(contractId);
            if (contract == null) {
                throw LedgerException.contractNotFound(contractId);
            }
            recordEvent(LedgerEvent.EventType.ARCHIVED, contract.templateId(), contractId,
                    contract.payload(), contract.witnesses());
            log.info("Ledger ARCHIVE {} contractId={}", contract.templateId(), contractId);
        }
    }

    /** Fetch an active contract's payload, asserting its template id. */
    @SuppressWarnings("unchecked")
    public <T> T fetch(String contractId, String expectedTemplateId, Class<T> type) {
        LedgerContract<?> contract = activeContracts.get(contractId);
        if (contract == null) {
            throw LedgerException.contractNotFound(contractId);
        }
        if (!contract.templateId().equals(expectedTemplateId)) {
            throw LedgerException.templateMismatch(contractId, expectedTemplateId);
        }
        return (T) type.cast(contract.payload());
    }

    /** Fetch the raw {@link LedgerContract} envelope (for visibility checks). */
    public LedgerContract<?> getContract(String contractId) {
        LedgerContract<?> contract = activeContracts.get(contractId);
        if (contract == null) {
            throw LedgerException.contractNotFound(contractId);
        }
        return contract;
    }

    public boolean exists(String contractId) {
        return activeContracts.containsKey(contractId);
    }

    /**
     * Query the Active Contract Set for all contracts of a template that
     * are visible to {@code party} (party is a signatory or observer) --
     * mirrors a Ledger API {@code GetActiveContracts} call scoped to a
     * single party, enforcing the "only relevant parties can view
     * transaction data" privacy rule.
     */
    @SuppressWarnings("unchecked")
    public <T> List<LedgerContract<T>> queryActive(String templateId, String party) {
        return activeContracts.values().stream()
                .filter(c -> c.templateId().equals(templateId))
                .filter(c -> c.witnesses().contains(party))
                .map(c -> (LedgerContract<T>) c)
                .collect(Collectors.toList());
    }

    /** Returns all active contracts of a template (admin/regulator view). */
    @SuppressWarnings("unchecked")
    public <T> List<LedgerContract<T>> queryAllActive(String templateId) {
        return activeContracts.values().stream()
                .filter(c -> c.templateId().equals(templateId))
                .map(c -> (LedgerContract<T>) c)
                .collect(Collectors.toList());
    }

    /** Returns the transaction event log visible to a given party, newest first. */
    public List<LedgerEvent> getTransactionStream(String party) {
        List<LedgerEvent> visible = transactionLog.stream()
                .filter(e -> e.witnessParties().contains(party))
                .collect(Collectors.toList());
        java.util.Collections.reverse(visible);
        return visible;
    }

    /** Full transaction log (regulator / audit view). */
    public List<LedgerEvent> getFullTransactionStream() {
        List<LedgerEvent> all = new ArrayList<>(transactionLog);
        java.util.Collections.reverse(all);
        return all;
    }

    private void recordEvent(LedgerEvent.EventType type, String templateId, String contractId,
                              Object payload, Set<String> witnesses) {
        transactionLog.add(new LedgerEvent(
                "evt-" + eventSeq.incrementAndGet(), type, templateId, contractId, payload, witnesses, Instant.now()));
    }

    private String nextContractId(String templateId) {
        return "csim:" + templateId + ":" + String.format("%08d", contractSeq.incrementAndGet());
    }
}
