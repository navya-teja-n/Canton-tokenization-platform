package com.canton.platform.controller;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.canton.platform.domain.contracts.TokenizedAsset;
import com.canton.platform.domain.contracts.TokenizedDeposit;
import com.canton.platform.dto.request.AssetIssueRequest;
import com.canton.platform.dto.request.DepositIssueRequest;
import com.canton.platform.service.AssetIssuanceService;
import com.canton.platform.web.CommandExecutor;

/**
 * Instrument issuance & lookup endpoints. Mirrors DAML templates
 * {@code GovernmentBondIssuance.IssueBond}, {@code TreasuryBillIssuance.IssueTreasuryBill}
 * and {@code DepositIssuanceRequest.IssueDeposit}: the issuer/bank creates
 * the instrument and it is simultaneously credited into the recipient's
 * KYC-gated custodial wallet.
 */
@RestController
@RequestMapping("/api/assets")
@Tag(name = "Assets", description = "Tokenized government bonds, treasury bills and bank deposits")
public class AssetController {

    private final AssetIssuanceService issuanceService;
    private final CommandExecutor commandExecutor;

    public AssetController(AssetIssuanceService issuanceService, CommandExecutor commandExecutor) {
        this.issuanceService = issuanceService;
        this.commandExecutor = commandExecutor;
    }

    @Operation(summary = "Issue a government bond or treasury bill into a wallet")
    @PostMapping("/issue")
    public ResponseEntity<TokenizedAsset> issue(@RequestBody @Valid AssetIssueRequest req,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        BigDecimal coupon = req.couponRatePct() != null ? req.couponRatePct() : BigDecimal.ZERO;
        BigDecimal purchasePrice = req.purchasePricePerUnit() != null ? req.purchasePricePerUnit() : req.faceValuePerUnit();
        TokenizedAsset result = commandExecutor.submit(idempotencyKey, req, "asset.issue:" + req.instrumentId(), () ->
                issuanceService.issueAsset(req.issuer(), req.owner(), req.custodian(), req.regulator(), req.walletId(),
                        req.assetClass(), req.instrumentId(), req.isin(), req.currency(),
                        req.faceValuePerUnit(), req.quantity(), coupon, purchasePrice,
                        req.issueDate(), req.maturityDate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Get a tokenized asset (bond/bill) by instrument id")
    @GetMapping("/{instrumentId}")
    public ResponseEntity<TokenizedAsset> get(@PathVariable String instrumentId) {
        return ResponseEntity.ok(issuanceService.getAsset(instrumentId));
    }

    @Operation(summary = "List tokenized assets owned by a party")
    @GetMapping("/owner/{owner}")
    public ResponseEntity<List<TokenizedAsset>> forOwner(@PathVariable String owner) {
        return ResponseEntity.ok(issuanceService.getAssetsForOwner(owner));
    }

    @Operation(summary = "Issue a tokenized bank deposit into a wallet")
    @PostMapping("/deposits/issue")
    public ResponseEntity<TokenizedDeposit> issueDeposit(@RequestBody @Valid DepositIssueRequest req,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TokenizedDeposit result = commandExecutor.submit(idempotencyKey, req, "deposit.issue:" + req.depositId(), () ->
                issuanceService.issueDeposit(req.bank(), req.owner(), req.regulator(), req.walletId(),
                        req.depositId(), req.currency(), req.amount()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Get a tokenized deposit by deposit id")
    @GetMapping("/deposits/{depositId}")
    public ResponseEntity<TokenizedDeposit> getDeposit(@PathVariable String depositId) {
        return ResponseEntity.ok(issuanceService.getDeposit(depositId));
    }

    @Operation(summary = "List tokenized deposits owned by a party")
    @GetMapping("/deposits/owner/{owner}")
    public ResponseEntity<List<TokenizedDeposit>> depositsForOwner(@PathVariable String owner) {
        return ResponseEntity.ok(issuanceService.getDepositsForOwner(owner));
    }
}
