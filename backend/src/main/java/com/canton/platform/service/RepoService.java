package com.canton.platform.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.TemplateIds;
import com.canton.platform.domain.contracts.RepoAgreement;
import com.canton.platform.domain.contracts.RepoClosedRecord;
import com.canton.platform.domain.contracts.RepoProposal;
import com.canton.platform.domain.contracts.RepoProposal.RepoDirection;
import com.canton.platform.domain.contracts.TokenizedAsset;
import com.canton.platform.domain.contracts.TokenizedDeposit;
import com.canton.platform.domain.enums.InstrumentStatus;
import com.canton.platform.domain.enums.RepoStatus;
import com.canton.platform.domain.enums.TradeStatus;
import com.canton.platform.events.EventBus;
import com.canton.platform.events.RepoLifecycleEvent;
import com.canton.platform.events.Topics;
import com.canton.platform.exception.InsufficientBalanceException;
import com.canton.platform.exception.InvalidStateTransitionException;
import com.canton.platform.exception.ResourceNotFoundException;
import com.canton.platform.exception.UnauthorizedActionException;
import com.canton.platform.ledger.CantonLedgerSimulator;

/**
 * Repurchase agreement (repo / reverse repo) workflow. Mirrors DAML module
 * {@code Repo.Repo}: {@code RepoProposal} / {@code ReverseRepoProposal} are
 * accepted into an open {@code RepoAgreement} via atomic DvP (collateral
 * locked with the lender, principal cash delivered to the borrower); the
 * agreement then transitions {@code RepoOpen -> RepoMatured -> RepoClosed |
 * RepoDefaulted}.
 */
@Service
public class RepoService {

    private static final Logger log = LoggerFactory.getLogger(RepoService.class);

    private final CantonLedgerSimulator ledger;
    private final AssetIssuanceService issuanceService;
    private final WalletService walletService;
    private final TradeService tradeService; // reuse transferAsset/transferCash primitives
    private final EventBus eventBus;

    /** repoId -> current contract id (RepoProposal | RepoAgreement | RepoClosedRecord) */
    private final ConcurrentHashMap<String, String> reposById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RepoStatus> repoStage = new ConcurrentHashMap<>();
    // repoStage uses RepoStatus loosely: REPO_OPEN/REPO_MATURED/REPO_CLOSED/REPO_DEFAULTED,
    // plus a sentinel "proposal" state tracked separately via proposalIds.
    private final ConcurrentHashMap<String, Boolean> isProposal = new ConcurrentHashMap<>();

    public RepoService(CantonLedgerSimulator ledger, AssetIssuanceService issuanceService,
                        WalletService walletService, TradeService tradeService, EventBus eventBus) {
        this.ledger = ledger;
        this.issuanceService = issuanceService;
        this.walletService = walletService;
        this.tradeService = tradeService;
        this.eventBus = eventBus;
    }

    /** Borrower (REPO) or lender (REVERSE_REPO) creates a repo proposal. */
    public RepoProposal proposeRepo(String borrower, String lender, String regulator, String repoId,
                                     RepoDirection direction, String collateralInstrumentId, BigDecimal collateralQty,
                                     Money principal, BigDecimal repoRatePct, LocalDate startDate, LocalDate maturityDate) {
        if (reposById.containsKey(repoId)) {
            throw new IllegalArgumentException("Repo id " + repoId + " already exists");
        }
        if (!maturityDate.isAfter(startDate)) {
            throw new InvalidStateTransitionException("maturityDate must be after startDate");
        }
        if (collateralQty.signum() <= 0) {
            throw new InvalidStateTransitionException("collateralQty must be positive");
        }
        if (principal.amount().signum() <= 0) {
            throw new InvalidStateTransitionException("principal amount must be positive");
        }
        RepoProposal proposal = RepoProposal.builder()
                .borrower(borrower).lender(lender).regulator(regulator).repoId(repoId)
                .direction(direction).collateralInstrumentId(collateralInstrumentId).collateralQty(collateralQty)
                .principal(principal).repoRatePct(repoRatePct).startDate(startDate).maturityDate(maturityDate)
                .status(TradeStatus.PROPOSED)
                .build();
        String signatory = direction == RepoDirection.REPO ? borrower : lender;
        String observer = direction == RepoDirection.REPO ? lender : borrower;
        String cid = ledger.create(TemplateIds.REPO_PROPOSAL, proposal, Set.of(signatory), Set.of(observer, regulator));
        reposById.put(repoId, cid);
        isProposal.put(repoId, true);
        log.info("Repo proposed: {} direction={} borrower={} lender={} collateral={} qty={} principal={}{}",
                repoId, direction, borrower, lender, collateralInstrumentId, collateralQty, principal.amount(), principal.currency());
        return proposal;
    }

    /**
     * Counterparty accepts: atomic DvP -- collateral transferred to the
     * lender and marked Collateralized, principal cash transferred to the
     * borrower. Mirrors {@code AcceptRepo} / {@code AcceptReverseRepo}.
     */
    public synchronized RepoAgreement acceptRepo(String repoId, String accepter,
                                                  String borrowerWalletId, String lenderWalletId,
                                                  String settledCollateralInstrumentId, String settledCashDepositId,
                                                  String correlationId) {
        RepoProposal proposal = getProposal(repoId);
        String expectedAccepter = proposal.direction() == RepoDirection.REPO ? proposal.lender() : proposal.borrower();
        if (!expectedAccepter.equals(accepter)) {
            throw new UnauthorizedActionException(accepter, "accept repo " + repoId);
        }
        if (proposal.status() != TradeStatus.PROPOSED) {
            throw new InvalidStateTransitionException("Repo " + repoId + " is not in PROPOSED status");
        }

        // --- Leg 1: collateral borrower -> lender, then mark Collateralized ---
        TokenizedAsset collateral = issuanceService.getAsset(proposal.collateralInstrumentId());
        if (!collateral.owner().equals(proposal.borrower())) {
            throw new UnauthorizedActionException(proposal.borrower(), "post collateral not owned by borrower");
        }
        if (collateral.status() != InstrumentStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Collateral " + proposal.collateralInstrumentId() + " is not Active");
        }
        if (collateral.quantity().compareTo(proposal.collateralQty()) < 0) {
            throw new InsufficientBalanceException("Borrower holds insufficient collateral quantity");
        }
        tradeService.transferAsset(collateral, proposal.collateralQty(), proposal.lender(), settledCollateralInstrumentId);
        markCollateralized(settledCollateralInstrumentId);

        // --- Leg 2: principal cash lender -> borrower ---
        TokenizedDeposit lenderCash = tradeService.findBuyerDeposit(proposal.lender(), proposal.principal().currency(), proposal.principal().amount());
        tradeService.transferCash(lenderCash, proposal.principal().amount(), proposal.borrower(), settledCashDepositId);

        // --- Wallet bookkeeping ---
        walletService.debitAsset(borrowerWalletId, proposal.collateralInstrumentId(), proposal.collateralQty());
        walletService.creditAsset(lenderWalletId, collateral.assetClass(), settledCollateralInstrumentId, proposal.collateralQty());
        walletService.lockCollateral(lenderWalletId, collateral.assetClass(), settledCollateralInstrumentId, proposal.collateralQty());
        walletService.debitCash(lenderWalletId, proposal.principal());
        walletService.creditCash(borrowerWalletId, proposal.principal());

        // --- Archive proposal, create open RepoAgreement ---
        ledger.archive(reposById.get(repoId));
        RepoAgreement agreement = RepoAgreement.builder()
                .borrower(proposal.borrower()).lender(proposal.lender()).regulator(proposal.regulator())
                .repoId(repoId).collateralInstrumentId(settledCollateralInstrumentId).collateralQty(proposal.collateralQty())
                .principal(proposal.principal()).repoRatePct(proposal.repoRatePct())
                .startDate(proposal.startDate()).maturityDate(proposal.maturityDate())
                .collateralContractId(issuanceService.getAssetContractId(settledCollateralInstrumentId))
                .status(RepoStatus.REPO_OPEN)
                .build();
        String cid = ledger.create(TemplateIds.REPO_AGREEMENT, agreement,
                Set.of(agreement.borrower(), agreement.lender()), Set.of(agreement.regulator()));
        reposById.put(repoId, cid);
        isProposal.put(repoId, false);
        repoStage.put(repoId, RepoStatus.REPO_OPEN);

        eventBus.publish(new RepoLifecycleEvent(repoId, agreement.borrower(), agreement.lender(),
                Topics.REPO_OPENED, "Collateral locked, principal disbursed", correlationId, Instant.now()));
        eventBus.publish(new com.canton.platform.events.CollateralEvent(lenderWalletId, settledCollateralInstrumentId,
                proposal.collateralQty(), Topics.COLLATERAL_LOCKED, correlationId, Instant.now()));
        log.info("Repo {} opened: collateral {} ({} units) locked with lender {}, principal {} {} to borrower {}",
                repoId, settledCollateralInstrumentId, proposal.collateralQty(), proposal.lender(),
                proposal.principal().amount(), proposal.principal().currency(), proposal.borrower());
        return agreement;
    }

    /** Either party / orchestration marks the repo as matured once {@code asOfDate >= maturityDate}. */
    public synchronized RepoAgreement markMatured(String repoId, LocalDate asOfDate, String correlationId) {
        RepoAgreement agreement = getAgreement(repoId);
        if (agreement.status() != RepoStatus.REPO_OPEN) {
            throw new InvalidStateTransitionException("Repo " + repoId + " must be OPEN to mature");
        }
        if (asOfDate.isBefore(agreement.maturityDate())) {
            throw new InvalidStateTransitionException("Cannot mark matured before maturity date " + agreement.maturityDate());
        }
        RepoAgreement updated = transitionAgreement(repoId, agreement.withStatus(RepoStatus.REPO_MATURED));
        repoStage.put(repoId, RepoStatus.REPO_MATURED);
        eventBus.publish(new RepoLifecycleEvent(repoId, agreement.borrower(), agreement.lender(),
                Topics.REPO_MATURED, "Maturity reached; awaiting repurchase", correlationId, Instant.now()));
        return updated;
    }

    /**
     * Borrower repurchases: pays principal + accrued interest to the lender,
     * collateral is returned and released. Mirrors {@code Repurchase}.
     */
    public synchronized RepoClosedRecord repurchase(String repoId, String borrower, LocalDate asOfDate,
                                                      String repaymentDepositId, String returnedInstrumentId,
                                                      String borrowerWalletId, String lenderWalletId,
                                                      String correlationId) {
        RepoAgreement agreement = getAgreement(repoId);
        if (!agreement.borrower().equals(borrower)) {
            throw new UnauthorizedActionException(borrower, "repurchase repo " + repoId);
        }
        if (agreement.status() != RepoStatus.REPO_OPEN && agreement.status() != RepoStatus.REPO_MATURED) {
            throw new InvalidStateTransitionException("Repo " + repoId + " must be OPEN or MATURED to repurchase");
        }

        BigDecimal interest = quoteInterest(agreement, asOfDate);
        BigDecimal totalDue = agreement.principal().amount().add(interest);
        Money totalDueMoney = new Money(agreement.principal().currency(), totalDue);

        // --- Leg 1: borrower repays principal + interest to lender ---
        TokenizedDeposit borrowerCash = tradeService.findBuyerDeposit(borrower, agreement.principal().currency(), totalDue);
        tradeService.transferCash(borrowerCash, totalDue, agreement.lender(), repaymentDepositId);

        // --- Leg 2: collateral returned lender -> borrower, released ---
        TokenizedAsset collateral = issuanceService.getAsset(agreement.collateralInstrumentId());
        tradeService.transferAsset(collateral, agreement.collateralQty(), borrower, returnedInstrumentId);
        releaseFromCollateral(returnedInstrumentId);

        // --- Wallet bookkeeping ---
        walletService.debitCash(borrowerWalletId, totalDueMoney);
        walletService.creditCash(lenderWalletId, totalDueMoney);
        walletService.releaseCollateral(lenderWalletId, agreement.collateralInstrumentId(), agreement.collateralQty());
        walletService.debitAsset(lenderWalletId, agreement.collateralInstrumentId(), agreement.collateralQty());
        walletService.creditAsset(borrowerWalletId, collateral.assetClass(), returnedInstrumentId, agreement.collateralQty());

        ledger.archive(reposById.get(repoId));
        RepoClosedRecord record = RepoClosedRecord.builder()
                .borrower(agreement.borrower()).lender(agreement.lender()).regulator(agreement.regulator())
                .repoId(repoId).collateralInstrumentId(returnedInstrumentId).collateralQty(agreement.collateralQty())
                .principal(agreement.principal()).interestPaid(interest)
                .startDate(agreement.startDate()).maturityDate(agreement.maturityDate())
                .closedAt(Instant.now()).outcome(RepoStatus.REPO_CLOSED)
                .build();
        String cid = ledger.create(TemplateIds.REPO_CLOSED_RECORD, record,
                Set.of(record.borrower(), record.lender()), Set.of(record.regulator()));
        reposById.put(repoId, cid);
        repoStage.put(repoId, RepoStatus.REPO_CLOSED);

        eventBus.publish(new RepoLifecycleEvent(repoId, agreement.borrower(), agreement.lender(),
                Topics.REPO_CLOSED, "Repurchased: principal " + agreement.principal().amount() + " + interest " + interest, correlationId, Instant.now()));
        eventBus.publish(new com.canton.platform.events.CollateralEvent(lenderWalletId, agreement.collateralInstrumentId(),
                agreement.collateralQty(), Topics.COLLATERAL_RELEASED, correlationId, Instant.now()));
        log.info("Repo {} repurchased by {}: paid {} {} (incl. interest {}), collateral returned",
                repoId, borrower, totalDue, agreement.principal().currency(), interest);
        return record;
    }

    /**
     * Lender declares default after maturity if the borrower has not
     * repurchased. The lender permanently retains the collateral. Mirrors
     * {@code DeclareDefault}.
     */
    public synchronized RepoClosedRecord declareDefault(String repoId, String lender, LocalDate asOfDate,
                                                          String releasedInstrumentId, String lenderWalletId,
                                                          String correlationId) {
        RepoAgreement agreement = getAgreement(repoId);
        if (!agreement.lender().equals(lender)) {
            throw new UnauthorizedActionException(lender, "declare default on repo " + repoId);
        }
        if (agreement.status() != RepoStatus.REPO_MATURED) {
            throw new InvalidStateTransitionException("Repo " + repoId + " must be MATURED to declare default");
        }
        if (!asOfDate.isAfter(agreement.maturityDate())) {
            throw new InvalidStateTransitionException("Default can only be declared after maturity date");
        }

        // Collateral stays with lender; simply release the Collateralized flag.
        releaseFromCollateral(agreement.collateralInstrumentId());
        walletService.releaseCollateral(lenderWalletId, agreement.collateralInstrumentId(), agreement.collateralQty());

        ledger.archive(reposById.get(repoId));
        RepoClosedRecord record = RepoClosedRecord.builder()
                .borrower(agreement.borrower()).lender(agreement.lender()).regulator(agreement.regulator())
                .repoId(repoId).collateralInstrumentId(agreement.collateralInstrumentId()).collateralQty(agreement.collateralQty())
                .principal(agreement.principal()).interestPaid(BigDecimal.ZERO)
                .startDate(agreement.startDate()).maturityDate(agreement.maturityDate())
                .closedAt(Instant.now()).outcome(RepoStatus.REPO_DEFAULTED)
                .build();
        String cid = ledger.create(TemplateIds.REPO_CLOSED_RECORD, record,
                Set.of(record.borrower(), record.lender()), Set.of(record.regulator()));
        reposById.put(repoId, cid);
        repoStage.put(repoId, RepoStatus.REPO_DEFAULTED);

        eventBus.publish(new RepoLifecycleEvent(repoId, agreement.borrower(), agreement.lender(),
                Topics.REPO_CLOSED, "Borrower defaulted; lender retains collateral", correlationId, Instant.now()));
        log.warn("Repo {} DEFAULTED: lender {} retains collateral {} ({} units)",
                repoId, lender, agreement.collateralInstrumentId(), agreement.collateralQty());
        return record;
    }

    /** Quotes principal + accrued repo interest as of a date, without mutating state. */
    public BigDecimal quoteRepurchaseAmount(String repoId, LocalDate asOfDate) {
        RepoAgreement agreement = getAgreement(repoId);
        return agreement.principal().amount().add(quoteInterest(agreement, asOfDate));
    }

    private BigDecimal quoteInterest(RepoAgreement agreement, LocalDate asOfDate) {
        long days = ChronoUnit.DAYS.between(agreement.startDate(), asOfDate);
        return Money.accrueInterest(agreement.principal().amount(), agreement.repoRatePct(), Math.max(days, 0));
    }

    public RepoProposal getProposal(String repoId) {
        String cid = reposById.get(repoId);
        if (cid == null || !Boolean.TRUE.equals(isProposal.get(repoId))) {
            throw new ResourceNotFoundException("No open repo proposal with id " + repoId);
        }
        return ledger.fetch(cid, TemplateIds.REPO_PROPOSAL, RepoProposal.class);
    }

    public RepoAgreement getAgreement(String repoId) {
        String cid = reposById.get(repoId);
        RepoStatus stage = repoStage.get(repoId);
        if (cid == null || stage == null || stage == RepoStatus.REPO_CLOSED || stage == RepoStatus.REPO_DEFAULTED) {
            throw new ResourceNotFoundException("No open repo agreement with id " + repoId);
        }
        return ledger.fetch(cid, TemplateIds.REPO_AGREEMENT, RepoAgreement.class);
    }

    public RepoClosedRecord getClosedRecord(String repoId) {
        String cid = reposById.get(repoId);
        RepoStatus stage = repoStage.get(repoId);
        if (cid == null || stage == null || (stage != RepoStatus.REPO_CLOSED && stage != RepoStatus.REPO_DEFAULTED)) {
            throw new ResourceNotFoundException("No closed repo record with id " + repoId);
        }
        return ledger.fetch(cid, TemplateIds.REPO_CLOSED_RECORD, RepoClosedRecord.class);
    }

    public List<RepoProposal> getProposalsForParty(String party) {
        return ledger.<RepoProposal>queryActive(TemplateIds.REPO_PROPOSAL, party).stream()
                .map(c -> c.payload()).toList();
    }

    public List<RepoAgreement> getAgreementsForParty(String party) {
        return ledger.<RepoAgreement>queryActive(TemplateIds.REPO_AGREEMENT, party).stream()
                .map(c -> c.payload()).toList();
    }

    public List<RepoClosedRecord> getClosedRecordsForParty(String party) {
        return ledger.<RepoClosedRecord>queryActive(TemplateIds.REPO_CLOSED_RECORD, party).stream()
                .map(c -> c.payload()).toList();
    }

    // -- internal: mirror GovernmentBond.MarkCollateralized / ReleaseCollateral on the asset record --

    private void markCollateralized(String instrumentId) {
        setAssetStatus(instrumentId, InstrumentStatus.COLLATERALIZED);
    }

    private void releaseFromCollateral(String instrumentId) {
        setAssetStatus(instrumentId, InstrumentStatus.ACTIVE);
    }

    private void setAssetStatus(String instrumentId, InstrumentStatus status) {
        TokenizedAsset asset = issuanceService.getAsset(instrumentId);
        String oldCid = issuanceService.getAssetContractId(instrumentId);
        ledger.archive(oldCid);
        TokenizedAsset updated = asset.withStatus(status);
        String newCid = ledger.create(TemplateIds.TOKENIZED_ASSET, updated,
                Set.of(updated.issuer()), Set.of(updated.owner(), updated.custodian(), updated.regulator()));
        issuanceService.putAsset(instrumentId, newCid);
    }

    private RepoAgreement transitionAgreement(String repoId, RepoAgreement updated) {
        String oldCid = reposById.get(repoId);
        ledger.archive(oldCid);
        String newCid = ledger.create(TemplateIds.REPO_AGREEMENT, updated,
                Set.of(updated.borrower(), updated.lender()), Set.of(updated.regulator()));
        reposById.put(repoId, newCid);
        return updated;
    }
}
