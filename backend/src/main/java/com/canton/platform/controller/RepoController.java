package com.canton.platform.controller;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.canton.platform.domain.contracts.RepoAgreement;
import com.canton.platform.domain.contracts.RepoClosedRecord;
import com.canton.platform.domain.contracts.RepoProposal;
import com.canton.platform.dto.request.RepoAcceptRequest;
import com.canton.platform.dto.request.RepoDefaultRequest;
import com.canton.platform.dto.request.RepoMatureRequest;
import com.canton.platform.dto.request.RepoProposeRequest;
import com.canton.platform.dto.request.RepoRepurchaseRequest;
import com.canton.platform.dto.response.RepoQuoteResponse;
import com.canton.platform.service.RepoService;
import com.canton.platform.web.CommandExecutor;
import com.canton.platform.web.Correlation;

/**
 * Repurchase agreement (repo / reverse repo) lifecycle endpoints. Mirrors
 * DAML module {@code Repo.Repo}: {@code propose -> accept -> [mature] ->
 * repurchase | default}. Acceptance settles atomically (DvP): collateral is
 * locked with the lender and principal cash is delivered to the borrower.
 * Maturity and closure publish {@code repo.matured} / {@code repo.closed} /
 * {@code collateral.released} events for downstream orchestration.
 */
@RestController
@RequestMapping("/api/repos")
@Tag(name = "Repos", description = "Repurchase agreements (repo / reverse repo) with collateral locking")
public class RepoController {

    private final RepoService repoService;
    private final CommandExecutor commandExecutor;

    public RepoController(RepoService repoService, CommandExecutor commandExecutor) {
        this.repoService = repoService;
        this.commandExecutor = commandExecutor;
    }

    @Operation(summary = "Propose a repo (borrower-initiated) or reverse repo (lender-initiated)")
    @PostMapping("/propose")
    public ResponseEntity<RepoProposal> propose(@RequestBody @Valid RepoProposeRequest req,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        RepoProposal result = commandExecutor.submit(idempotencyKey, req, "repo.propose:" + req.repoId(), () ->
                repoService.proposeRepo(req.borrower(), req.lender(), req.regulator(), req.repoId(),
                        req.direction(), req.collateralInstrumentId(), req.collateralQty(),
                        req.principal(), req.repoRatePct(), req.startDate(), req.maturityDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Counterparty accepts: atomic DvP collateral lock + principal disbursement")
    @PostMapping("/{repoId}/accept")
    public ResponseEntity<RepoAgreement> accept(@PathVariable String repoId,
                                                  @RequestBody @Valid RepoAcceptRequest req,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        RepoAgreement result = commandExecutor.submit(idempotencyKey, req, "repo.accept:" + repoId, () ->
                repoService.acceptRepo(repoId, req.accepter(), req.borrowerWalletId(), req.lenderWalletId(),
                        req.settledCollateralInstrumentId(), req.settledCashDepositId(), Correlation.idOrNew(req.correlationId())));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Mark the repo as matured (asOfDate >= maturityDate)")
    @PostMapping("/{repoId}/mature")
    public ResponseEntity<RepoAgreement> mature(@PathVariable String repoId,
                                                   @RequestBody @Valid RepoMatureRequest req,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        RepoAgreement result = commandExecutor.submit(idempotencyKey, req, "repo.mature:" + repoId, () ->
                repoService.markMatured(repoId, req.asOfDate(), Correlation.idOrNew(req.correlationId())));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Borrower repurchases: repay principal + accrued interest, collateral returned")
    @PostMapping("/{repoId}/repurchase")
    public ResponseEntity<RepoClosedRecord> repurchase(@PathVariable String repoId,
                                                          @RequestBody @Valid RepoRepurchaseRequest req,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        RepoClosedRecord result = commandExecutor.submit(idempotencyKey, req, "repo.repurchase:" + repoId, () ->
                repoService.repurchase(repoId, req.borrower(), req.asOfDate(), req.repaymentDepositId(),
                        req.returnedInstrumentId(), req.borrowerWalletId(), req.lenderWalletId(),
                        Correlation.idOrNew(req.correlationId())));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Lender declares default after maturity: collateral permanently retained")
    @PostMapping("/{repoId}/default")
    public ResponseEntity<RepoClosedRecord> declareDefault(@PathVariable String repoId,
                                                              @RequestBody @Valid RepoDefaultRequest req,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        RepoClosedRecord result = commandExecutor.submit(idempotencyKey, req, "repo.default:" + repoId, () ->
                repoService.declareDefault(repoId, req.lender(), req.asOfDate(), req.releasedInstrumentId(),
                        req.lenderWalletId(), Correlation.idOrNew(req.correlationId())));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Quote the repurchase amount (principal + accrued interest) as of a date")
    @GetMapping("/{repoId}/quote")
    public ResponseEntity<RepoQuoteResponse> quote(@PathVariable String repoId,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ResponseEntity.ok(new RepoQuoteResponse(repoId, asOfDate, repoService.quoteRepurchaseAmount(repoId, asOfDate)));
    }

    @Operation(summary = "Get an open repo proposal by id")
    @GetMapping("/{repoId}/proposal")
    public ResponseEntity<RepoProposal> getProposal(@PathVariable String repoId) {
        return ResponseEntity.ok(repoService.getProposal(repoId));
    }

    @Operation(summary = "Get an open repo agreement by id")
    @GetMapping("/{repoId}/agreement")
    public ResponseEntity<RepoAgreement> getAgreement(@PathVariable String repoId) {
        return ResponseEntity.ok(repoService.getAgreement(repoId));
    }

    @Operation(summary = "Get a closed repo record (repurchased or defaulted) by id")
    @GetMapping("/{repoId}/closed")
    public ResponseEntity<RepoClosedRecord> getClosed(@PathVariable String repoId) {
        return ResponseEntity.ok(repoService.getClosedRecord(repoId));
    }

    @Operation(summary = "List open repo proposals visible to a party")
    @GetMapping("/party/{party}/proposals")
    public ResponseEntity<List<RepoProposal>> proposalsForParty(@PathVariable String party) {
        return ResponseEntity.ok(repoService.getProposalsForParty(party));
    }

    @Operation(summary = "List open repo agreements visible to a party")
    @GetMapping("/party/{party}/agreements")
    public ResponseEntity<List<RepoAgreement>> agreementsForParty(@PathVariable String party) {
        return ResponseEntity.ok(repoService.getAgreementsForParty(party));
    }

    @Operation(summary = "List closed repo records visible to a party")
    @GetMapping("/party/{party}/closed")
    public ResponseEntity<List<RepoClosedRecord>> closedForParty(@PathVariable String party) {
        return ResponseEntity.ok(repoService.getClosedRecordsForParty(party));
    }
}
