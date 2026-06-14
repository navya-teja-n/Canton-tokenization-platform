package com.canton.platform.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.canton.platform.domain.contracts.Wallet;
import com.canton.platform.dto.request.WalletOpenRequest;
import com.canton.platform.service.WalletService;
import com.canton.platform.web.CommandExecutor;

/**
 * Custodial wallet management endpoints. Mirrors DAML templates
 * {@code Wallet.Wallet.WalletApplication} / {@code Wallet}. Opening a
 * wallet requires the owner to already hold an active {@code KycApproval}
 * (enforced by {@link WalletService#openWallet}).
 */
@RestController
@RequestMapping("/api/wallets")
@Tag(name = "Wallets", description = "KYC-gated custodial wallet management")
public class WalletController {

    private final WalletService walletService;
    private final CommandExecutor commandExecutor;

    public WalletController(WalletService walletService, CommandExecutor commandExecutor) {
        this.walletService = walletService;
        this.commandExecutor = commandExecutor;
    }

    @Operation(summary = "Open a new KYC-gated custodial wallet")
    @PostMapping
    public ResponseEntity<Wallet> open(@RequestBody @Valid WalletOpenRequest req,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Wallet result = commandExecutor.submit(idempotencyKey, req, "wallet.open:" + req.walletId(), () ->
                walletService.openWallet(req.owner(), req.custodian(), req.regulator(), req.kycProvider(), req.walletId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Get a wallet by id (holdings + cash balances)")
    @GetMapping("/{walletId}")
    public ResponseEntity<Wallet> get(@PathVariable String walletId) {
        return ResponseEntity.ok(walletService.getWallet(walletId));
    }

    @Operation(summary = "List wallets visible to a party (owner, custodian, or regulator)")
    @GetMapping("/party/{party}")
    public ResponseEntity<List<Wallet>> forParty(@PathVariable String party) {
        return ResponseEntity.ok(walletService.getWalletsForParty(party));
    }
}
