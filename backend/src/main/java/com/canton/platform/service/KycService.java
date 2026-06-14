package com.canton.platform.service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.canton.platform.domain.TemplateIds;
import com.canton.platform.domain.contracts.KycApproval;
import com.canton.platform.domain.enums.KycStatus;
import com.canton.platform.events.EventBus;
import com.canton.platform.events.KycStatusChangedEvent;
import com.canton.platform.exception.KycNotApprovedException;
import com.canton.platform.exception.ResourceNotFoundException;
import com.canton.platform.ledger.CantonLedgerSimulator;
import com.canton.platform.ledger.LedgerContract;

/**
 * Simulated KYC verification service. Mirrors DAML templates
 * {@code KYC.KycService.KycRequest} / {@code KycApproval}: a participant
 * must have an active {@code KycApproval} with status {@code KYC_APPROVED}
 * before a custodial wallet can be opened or any asset-affecting choice
 * exercised on their behalf.
 */
@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final CantonLedgerSimulator ledger;
    private final EventBus eventBus;

    /** applicant party -> current KycApproval contract id */
    private final ConcurrentHashMap<String, String> approvalsByApplicant = new ConcurrentHashMap<>();

    public KycService(CantonLedgerSimulator ledger, EventBus eventBus) {
        this.ledger = ledger;
        this.eventBus = eventBus;
    }

    /** KYC provider reviews and approves an applicant. */
    public KycApproval approve(String applicant, String kycProvider, String regulator,
                                String legalName, String jurisdiction, String riskRating,
                                String correlationId) {
        KycApproval approval = KycApproval.builder()
                .applicant(applicant)
                .kycProvider(kycProvider)
                .regulator(regulator)
                .legalName(legalName)
                .jurisdiction(jurisdiction)
                .status(KycStatus.KYC_APPROVED)
                .riskRating(riskRating)
                .approvedAt(Instant.now())
                .build();
        replaceApproval(applicant, approval);
        log.info("KYC approved for applicant={} provider={} risk={}", applicant, kycProvider, riskRating);
        eventBus.publish(new KycStatusChangedEvent(applicant, KycStatus.KYC_APPROVED, correlationId, Instant.now()));
        return approval;
    }

    /** KYC provider rejects an applicant. */
    public KycApproval reject(String applicant, String kycProvider, String regulator,
                               String legalName, String jurisdiction, String reason,
                               String correlationId) {
        KycApproval approval = KycApproval.builder()
                .applicant(applicant)
                .kycProvider(kycProvider)
                .regulator(regulator)
                .legalName(legalName)
                .jurisdiction(jurisdiction)
                .status(KycStatus.KYC_REJECTED)
                .riskRating(reason)
                .approvedAt(Instant.now())
                .build();
        replaceApproval(applicant, approval);
        eventBus.publish(new KycStatusChangedEvent(applicant, KycStatus.KYC_REJECTED, correlationId, Instant.now()));
        return approval;
    }

    /** Regulator/KYC provider revokes a previously-approved applicant. */
    public KycApproval revoke(String applicant, String reason, String correlationId) {
        KycApproval current = getApproval(applicant);
        KycApproval revoked = current.withStatus(KycStatus.KYC_REVOKED).withRiskRating(reason);
        replaceApproval(applicant, revoked);
        eventBus.publish(new KycStatusChangedEvent(applicant, KycStatus.KYC_REVOKED, correlationId, Instant.now()));
        return revoked;
    }

    public KycApproval getApproval(String applicant) {
        String contractId = approvalsByApplicant.get(applicant);
        if (contractId == null) {
            throw new ResourceNotFoundException("No KYC record found for applicant " + applicant);
        }
        return ledger.fetch(contractId, TemplateIds.KYC_APPROVAL, KycApproval.class);
    }

    public boolean isApproved(String applicant) {
        String contractId = approvalsByApplicant.get(applicant);
        if (contractId == null) return false;
        KycApproval approval = ledger.fetch(contractId, TemplateIds.KYC_APPROVAL, KycApproval.class);
        return approval.status() == KycStatus.KYC_APPROVED;
    }

    /** Throws {@link KycNotApprovedException} if the party is not KYC-approved. */
    public void requireApproved(String party) {
        if (!isApproved(party)) {
            throw new KycNotApprovedException(party);
        }
    }

    private void replaceApproval(String applicant, KycApproval approval) {
        String existing = approvalsByApplicant.get(applicant);
        if (existing != null && ledger.exists(existing)) {
            ledger.archive(existing);
        }
        String newId = ledger.create(TemplateIds.KYC_APPROVAL, approval,
                Set.of(approval.kycProvider()), Set.of(approval.applicant(), approval.regulator()));
        approvalsByApplicant.put(applicant, newId);
    }
}
