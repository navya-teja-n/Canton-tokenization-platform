package com.canton.platform.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.TemplateIds;
import com.canton.platform.domain.contracts.TokenizedAsset;
import com.canton.platform.domain.contracts.TokenizedDeposit;
import com.canton.platform.domain.enums.AssetClass;
import com.canton.platform.domain.enums.InstrumentStatus;
import com.canton.platform.exception.ResourceNotFoundException;
import com.canton.platform.ledger.CantonLedgerSimulator;

/**
 * Issuance of new financial instruments. Mirrors DAML templates
 * {@code GovernmentBondIssuance.IssueBond},
 * {@code TreasuryBillIssuance.IssueTreasuryBill}, and
 * {@code DepositIssuanceRequest.IssueDeposit}: the issuer (sole signatory)
 * creates the instrument and it is simultaneously credited to the
 * recipient's custodial wallet.
 */
@Service
public class AssetIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(AssetIssuanceService.class);

    private final CantonLedgerSimulator ledger;
    private final WalletService walletService;
    private final KycService kycService;

    /** instrumentId -> current TokenizedAsset contract id */
    private final ConcurrentHashMap<String, String> assetsById = new ConcurrentHashMap<>();
    /** depositId -> current TokenizedDeposit contract id */
    private final ConcurrentHashMap<String, String> depositsById = new ConcurrentHashMap<>();

    public AssetIssuanceService(CantonLedgerSimulator ledger, WalletService walletService, KycService kycService) {
        this.ledger = ledger;
        this.walletService = walletService;
        this.kycService = kycService;
    }

    /** Issues a government bond or treasury bill directly into the owner's wallet. */
    public TokenizedAsset issueAsset(String issuer, String owner, String custodian, String regulator,
                                      String walletId, AssetClass assetClass, String instrumentId, String isin,
                                      String currency, BigDecimal faceValuePerUnit, BigDecimal quantity,
                                      BigDecimal couponRatePct, BigDecimal purchasePricePerUnit,
                                      LocalDate issueDate, LocalDate maturityDate) {
        kycService.requireApproved(owner);
        if (assetsById.containsKey(instrumentId)) {
            throw new IllegalArgumentException("Instrument id " + instrumentId + " already exists");
        }
        TokenizedAsset asset = TokenizedAsset.builder()
                .issuer(issuer).owner(owner).custodian(custodian).regulator(regulator)
                .instrumentId(instrumentId).isin(isin).assetClass(assetClass).currency(currency)
                .faceValuePerUnit(faceValuePerUnit).quantity(quantity)
                .couponRatePct(couponRatePct).purchasePricePerUnit(purchasePricePerUnit)
                .issueDate(issueDate).maturityDate(maturityDate)
                .status(InstrumentStatus.ACTIVE)
                .build();
        String cid = ledger.create(TemplateIds.TOKENIZED_ASSET, asset,
                Set.of(issuer), Set.of(owner, custodian, regulator));
        assetsById.put(instrumentId, cid);

        walletService.creditAsset(walletId, assetClass, instrumentId, quantity);
        log.info("Issued {} {} units of {} ({}) to wallet {}", quantity, assetClass, instrumentId, isin, walletId);
        return asset;
    }

    /** Issues a tokenized bank deposit directly into the owner's wallet cash balance. */
    public TokenizedDeposit issueDeposit(String bank, String owner, String regulator, String walletId,
                                          String depositId, String currency, BigDecimal amount) {
        kycService.requireApproved(owner);
        if (depositsById.containsKey(depositId)) {
            throw new IllegalArgumentException("Deposit id " + depositId + " already exists");
        }
        TokenizedDeposit deposit = TokenizedDeposit.builder()
                .bank(bank).owner(owner).regulator(regulator)
                .depositId(depositId).currency(currency).amount(amount)
                .issuedAt(Instant.now()).frozen(false)
                .build();
        String cid = ledger.create(TemplateIds.TOKENIZED_DEPOSIT, deposit, Set.of(bank), Set.of(owner, regulator));
        depositsById.put(depositId, cid);

        walletService.creditCash(walletId, new Money(currency, amount));
        log.info("Issued tokenized deposit {} of {} {} to wallet {}", depositId, amount, currency, walletId);
        return deposit;
    }

    public TokenizedAsset getAsset(String instrumentId) {
        String cid = assetsById.get(instrumentId);
        if (cid == null) throw new ResourceNotFoundException("No instrument found with id " + instrumentId);
        return ledger.fetch(cid, TemplateIds.TOKENIZED_ASSET, TokenizedAsset.class);
    }

    public String getAssetContractId(String instrumentId) {
        String cid = assetsById.get(instrumentId);
        if (cid == null) throw new ResourceNotFoundException("No instrument found with id " + instrumentId);
        return cid;
    }

    public TokenizedDeposit getDeposit(String depositId) {
        String cid = depositsById.get(depositId);
        if (cid == null) throw new ResourceNotFoundException("No deposit found with id " + depositId);
        return ledger.fetch(cid, TemplateIds.TOKENIZED_DEPOSIT, TokenizedDeposit.class);
    }

    public String getDepositContractId(String depositId) {
        String cid = depositsById.get(depositId);
        if (cid == null) throw new ResourceNotFoundException("No deposit found with id " + depositId);
        return cid;
    }

    public List<TokenizedAsset> getAssetsForOwner(String owner) {
        return ledger.<TokenizedAsset>queryAllActive(TemplateIds.TOKENIZED_ASSET).stream()
                .map(c -> c.payload()).filter(a -> a.owner().equals(owner)).toList();
    }

    public List<TokenizedDeposit> getDepositsForOwner(String owner) {
        return ledger.<TokenizedDeposit>queryAllActive(TemplateIds.TOKENIZED_DEPOSIT).stream()
                .map(c -> c.payload()).filter(d -> d.owner().equals(owner)).toList();
    }

    // -- Internal helpers used by TradeService / RepoService for atomic transfers --

    void putAsset(String instrumentId, String contractId) {
        assetsById.put(instrumentId, contractId);
    }

    void removeAsset(String instrumentId) {
        assetsById.remove(instrumentId);
    }

    void putDeposit(String depositId, String contractId) {
        depositsById.put(depositId, contractId);
    }

    void removeDeposit(String depositId) {
        depositsById.remove(depositId);
    }

    CantonLedgerSimulator ledger() {
        return ledger;
    }
}
