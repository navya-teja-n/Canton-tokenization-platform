package com.canton.platform.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.TemplateIds;
import com.canton.platform.domain.contracts.ExecutedTrade;
import com.canton.platform.domain.contracts.TokenizedAsset;
import com.canton.platform.domain.contracts.TokenizedDeposit;
import com.canton.platform.domain.contracts.TradeProposal;
import com.canton.platform.domain.enums.AssetClass;
import com.canton.platform.domain.enums.InstrumentStatus;
import com.canton.platform.domain.enums.TradeStatus;
import com.canton.platform.events.EventBus;
import com.canton.platform.events.TradeExecutedEvent;
import com.canton.platform.exception.InsufficientBalanceException;
import com.canton.platform.exception.InvalidStateTransitionException;
import com.canton.platform.exception.ResourceNotFoundException;
import com.canton.platform.exception.UnauthorizedActionException;
import com.canton.platform.ledger.CantonLedgerSimulator;

/**
 * Bilateral trade (Delivery-vs-Payment) workflow. Mirrors DAML templates
 * {@code Trading.Trade.BondTradeProposal} / {@code BillTradeProposal} and
 * their {@code AcceptAndSettle} choice: settlement atomically transfers the
 * asset from seller to buyer and pays cash from buyer to seller within a
 * single synchronized operation -- if any step fails, no ledger mutation is
 * committed (DvP atomicity).
 */
@Service
public class TradeService {

    private static final Logger log = LoggerFactory.getLogger(TradeService.class);

    private final CantonLedgerSimulator ledger;
    private final AssetIssuanceService issuanceService;
    private final WalletService walletService;
    private final EventBus eventBus;

    /** tradeId -> current TradeProposal/ExecutedTrade contract id */
    private final ConcurrentHashMap<String, String> tradesById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> executed = new ConcurrentHashMap<>();

    public TradeService(CantonLedgerSimulator ledger, AssetIssuanceService issuanceService,
                         WalletService walletService, EventBus eventBus) {
        this.ledger = ledger;
        this.issuanceService = issuanceService;
        this.walletService = walletService;
        this.eventBus = eventBus;
    }

    /** Seller proposes a trade. Mirrors creating a {@code BondTradeProposal}. */
    public TradeProposal proposeTrade(String seller, String buyer, String regulator, String tradeId,
                                       AssetClass assetClass, String instrumentId, BigDecimal quantity, Money price) {
        if (tradesById.containsKey(tradeId)) {
            throw new IllegalArgumentException("Trade id " + tradeId + " already exists");
        }
        TokenizedAsset asset = issuanceService.getAsset(instrumentId);
        if (!asset.owner().equals(seller)) {
            throw new UnauthorizedActionException(seller, "propose trade for instrument not owned by seller");
        }
        if (asset.status() != InstrumentStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Instrument " + instrumentId + " is not Active (status=" + asset.status() + ")");
        }
        if (asset.quantity().compareTo(quantity) < 0) {
            throw new InsufficientBalanceException("Seller holds insufficient quantity of " + instrumentId);
        }

        TradeProposal proposal = TradeProposal.builder()
                .seller(seller).buyer(buyer).regulator(regulator).tradeId(tradeId)
                .assetClass(assetClass).instrumentId(instrumentId).quantity(quantity).price(price)
                .status(TradeStatus.PROPOSED)
                .build();
        String cid = ledger.create(TemplateIds.TRADE_PROPOSAL, proposal, Set.of(seller), Set.of(buyer, regulator));
        tradesById.put(tradeId, cid);
        log.info("Trade proposed: {} seller={} buyer={} instrument={} qty={} price={}{}",
                tradeId, seller, buyer, instrumentId, quantity, price.amount(), price.currency());
        return proposal;
    }

    /**
     * Buyer accepts and the trade settles atomically (DvP). Mirrors
     * {@code BondTradeProposal.AcceptAndSettle}.
     */
    public synchronized ExecutedTrade acceptAndSettle(String tradeId, String buyer,
                                                        String sellerWalletId, String buyerWalletId,
                                                        String settledInstrumentId, String settledDepositId,
                                                        String correlationId) {
        TradeProposal proposal = getProposal(tradeId);
        if (!proposal.buyer().equals(buyer)) {
            throw new UnauthorizedActionException(buyer, "accept trade " + tradeId);
        }
        if (proposal.status() != TradeStatus.PROPOSED) {
            throw new InvalidStateTransitionException("Trade " + tradeId + " is not in PROPOSED status");
        }

        // --- Leg 1: transfer the asset from seller to buyer ---
        TokenizedAsset asset = issuanceService.getAsset(proposal.instrumentId());
        if (asset.status() != InstrumentStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Instrument " + proposal.instrumentId() + " is not Active");
        }
        if (asset.quantity().compareTo(proposal.quantity()) < 0) {
            throw new InsufficientBalanceException("Seller's position in " + proposal.instrumentId() + " is insufficient");
        }
        transferAsset(asset, proposal.quantity(), buyer, settledInstrumentId);

        // --- Leg 2: pay cash from buyer to seller ---
        TokenizedDeposit deposit = findBuyerDeposit(buyer, proposal.price().currency(), proposal.price().amount());
        transferCash(deposit, proposal.price().amount(), proposal.seller(), settledDepositId);

        // --- Wallet bookkeeping ---
        walletService.debitAsset(sellerWalletId, proposal.instrumentId(), proposal.quantity());
        walletService.creditAsset(buyerWalletId, proposal.assetClass(), settledInstrumentId, proposal.quantity());
        walletService.debitCash(buyerWalletId, proposal.price());
        walletService.creditCash(sellerWalletId, proposal.price());

        // --- Archive proposal, create ExecutedTrade ---
        ledger.archive(tradesById.get(tradeId));
        ExecutedTrade executedTrade = ExecutedTrade.builder()
                .seller(proposal.seller()).buyer(proposal.buyer()).regulator(proposal.regulator())
                .tradeId(tradeId).assetClass(proposal.assetClass()).instrumentId(settledInstrumentId)
                .quantity(proposal.quantity()).price(proposal.price())
                .settledAssetContractId(issuanceService.getAssetContractId(settledInstrumentId))
                .settledCashContractId(issuanceService.getDepositContractId(settledDepositId))
                .settledAt(Instant.now())
                .build();
        String cid = ledger.create(TemplateIds.EXECUTED_TRADE, executedTrade,
                Set.of(proposal.seller(), proposal.buyer()), Set.of(proposal.regulator()));
        tradesById.put(tradeId, cid);
        executed.put(tradeId, true);

        eventBus.publish(new TradeExecutedEvent(tradeId, proposal.seller(), proposal.buyer(),
                settledInstrumentId, correlationId, Instant.now()));
        log.info("Trade {} settled (DvP): {} units of {} <-> {} {}", tradeId, proposal.quantity(),
                proposal.instrumentId(), proposal.price().amount(), proposal.price().currency());
        return executedTrade;
    }

    /** Buyer rejects the proposal. */
    public TradeProposal reject(String tradeId, String buyer, String reason) {
        TradeProposal proposal = getProposal(tradeId);
        if (!proposal.buyer().equals(buyer)) {
            throw new UnauthorizedActionException(buyer, "reject trade " + tradeId);
        }
        return transitionProposal(tradeId, proposal.withStatus(TradeStatus.REJECTED));
    }

    /** Seller cancels the proposal before acceptance. */
    public TradeProposal cancel(String tradeId, String seller) {
        TradeProposal proposal = getProposal(tradeId);
        if (!proposal.seller().equals(seller)) {
            throw new UnauthorizedActionException(seller, "cancel trade " + tradeId);
        }
        return transitionProposal(tradeId, proposal.withStatus(TradeStatus.CANCELLED));
    }

    public TradeProposal getProposal(String tradeId) {
        String cid = tradesById.get(tradeId);
        if (cid == null || executed.containsKey(tradeId)) {
            throw new ResourceNotFoundException("No open trade proposal with id " + tradeId);
        }
        return ledger.fetch(cid, TemplateIds.TRADE_PROPOSAL, TradeProposal.class);
    }

    public ExecutedTrade getExecutedTrade(String tradeId) {
        String cid = tradesById.get(tradeId);
        if (cid == null || !executed.containsKey(tradeId)) {
            throw new ResourceNotFoundException("No executed trade with id " + tradeId);
        }
        return ledger.fetch(cid, TemplateIds.EXECUTED_TRADE, ExecutedTrade.class);
    }

    public List<TradeProposal> getProposalsForParty(String party) {
        return ledger.<TradeProposal>queryActive(TemplateIds.TRADE_PROPOSAL, party).stream()
                .map(c -> c.payload()).toList();
    }

    public List<ExecutedTrade> getExecutedTradesForParty(String party) {
        return ledger.<ExecutedTrade>queryActive(TemplateIds.EXECUTED_TRADE, party).stream()
                .map(c -> c.payload()).toList();
    }

    // ---------------------------------------------------------------------
    // Internal helpers shared with RepoService-style settlement primitives
    // ---------------------------------------------------------------------

    /** Splits/transfers a TokenizedAsset position to a new owner. */
    void transferAsset(TokenizedAsset asset, BigDecimal transferQty, String newOwner, String newInstrumentId) {
        String oldCid = issuanceService.getAssetContractId(asset.instrumentId());
        ledger.archive(oldCid);

        BigDecimal remainder = asset.quantity().subtract(transferQty);
        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            TokenizedAsset remainderAsset = asset.withQuantity(remainder);
            String remCid = ledger.create(TemplateIds.TOKENIZED_ASSET, remainderAsset,
                    Set.of(remainderAsset.issuer()), Set.of(remainderAsset.owner(), remainderAsset.custodian(), remainderAsset.regulator()));
            issuanceService.putAsset(asset.instrumentId(), remCid);
        } else {
            issuanceService.removeAsset(asset.instrumentId());
        }

        TokenizedAsset transferred = asset.withOwner(newOwner).withQuantity(transferQty).withInstrumentId(newInstrumentId);
        String newCid = ledger.create(TemplateIds.TOKENIZED_ASSET, transferred,
                Set.of(transferred.issuer()), Set.of(transferred.owner(), transferred.custodian(), transferred.regulator()));
        issuanceService.putAsset(newInstrumentId, newCid);
    }

    /** Splits/transfers a TokenizedDeposit (cash) position to a new owner. */
    void transferCash(TokenizedDeposit deposit, BigDecimal payAmount, String payee, String newDepositId) {
        String oldCid = issuanceService.getDepositContractId(deposit.depositId());
        ledger.archive(oldCid);

        BigDecimal remainder = deposit.amount().subtract(payAmount);
        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            TokenizedDeposit remainderDeposit = deposit.withAmount(remainder);
            String remCid = ledger.create(TemplateIds.TOKENIZED_DEPOSIT, remainderDeposit,
                    Set.of(remainderDeposit.bank()), Set.of(remainderDeposit.owner(), remainderDeposit.regulator()));
            issuanceService.putDeposit(deposit.depositId(), remCid);
        } else {
            issuanceService.removeDeposit(deposit.depositId());
        }

        TokenizedDeposit transferred = deposit.withOwner(payee).withAmount(payAmount).withDepositId(newDepositId);
        String newCid = ledger.create(TemplateIds.TOKENIZED_DEPOSIT, transferred,
                Set.of(transferred.bank()), Set.of(transferred.owner(), transferred.regulator()));
        issuanceService.putDeposit(newDepositId, newCid);
    }

    /** Finds a deposit owned by {@code party} in {@code currency} with at least {@code amount}, not frozen. */
    TokenizedDeposit findBuyerDeposit(String party, String currency, BigDecimal amount) {
        return issuanceService.getDepositsForOwner(party).stream()
                .filter(d -> d.currency().equals(currency) && !d.frozen() && d.amount().compareTo(amount) >= 0)
                .findFirst()
                .orElseThrow(() -> new InsufficientBalanceException(
                        "Party " + party + " has no unfrozen deposit with sufficient balance in " + currency));
    }

    private TradeProposal transitionProposal(String tradeId, TradeProposal updated) {
        String oldCid = tradesById.get(tradeId);
        ledger.archive(oldCid);
        String newCid = ledger.create(TemplateIds.TRADE_PROPOSAL, updated,
                Set.of(updated.seller()), Set.of(updated.buyer(), updated.regulator()));
        tradesById.put(tradeId, newCid);
        return updated;
    }
}
