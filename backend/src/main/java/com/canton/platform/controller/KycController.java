package com.canton.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.canton.platform.domain.contracts.KycApproval;
import com.canton.platform.dto.request.KycApproveRequest;
import com.canton.platform.dto.request.KycRejectRequest;
import com.canton.platform.dto.request.KycRevokeRequest;
import com.canton.platform.service.KycService;
import com.canton.platform.web.CommandExecutor;
import com.canton.platform.web.Correlation;

/**
 * Simulated KYC verification service endpoints. Mirrors DAML templates
 * {@code KYC.KycService.KycRequest} / {@code KycApproval}: every other
 * service in the platform (wallet opening, asset issuance) calls
 * {@code KycService.requireApproved} before allowing a party to hold or
 * trade assets.
 */
@RestController
@RequestMapping("/api/kyc")
@Tag(name = "KYC", description = "KYC verification and custodial-access control")
public class KycController {

    private final KycService kycService;
    private final CommandExecutor commandExecutor;

    public KycController(KycService kycService, CommandExecutor commandExecutor) {
        this.kycService = kycService;
        this.commandExecutor = commandExecutor;
    }

    @Operation(summary = "Approve a KYC applicant")
    @PostMapping("/approve")
    public ResponseEntity<KycApproval> approve(@RequestBody @Valid KycApproveRequest req,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        KycApproval result = commandExecutor.submit(idempotencyKey, req, "kyc.approve:" + req.applicant(), () ->
                kycService.approve(req.applicant(), req.kycProvider(), req.regulator(),
                        req.legalName(), req.jurisdiction(), req.riskRating(), Correlation.idOrNew(req.correlationId())));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Reject a KYC applicant")
    @PostMapping("/reject")
    public ResponseEntity<KycApproval> reject(@RequestBody @Valid KycRejectRequest req,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        KycApproval result = commandExecutor.submit(idempotencyKey, req, "kyc.reject:" + req.applicant(), () ->
                kycService.reject(req.applicant(), req.kycProvider(), req.regulator(),
                        req.legalName(), req.jurisdiction(), req.reason(), Correlation.idOrNew(req.correlationId())));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Revoke a previously approved KYC record")
    @PostMapping("/revoke")
    public ResponseEntity<KycApproval> revoke(@RequestBody @Valid KycRevokeRequest req,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        KycApproval result = commandExecutor.submit(idempotencyKey, req, "kyc.revoke:" + req.applicant(), () ->
                kycService.revoke(req.applicant(), req.reason(), Correlation.idOrNew(req.correlationId())));
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get the current KYC status for a party")
    @GetMapping("/{applicant}")
    public ResponseEntity<KycApproval> get(@PathVariable String applicant) {
        return ResponseEntity.ok(kycService.getApproval(applicant));
    }
}
