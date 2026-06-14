package com.canton.platform.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.TemplateIds;
import com.canton.platform.domain.contracts.Wallet;
import com.canton.platform.domain.contracts.WalletHolding;
import com.canton.platform.domain.enums.AssetClass;
import com.canton.platform.exception.InsufficientBalanceException;
import com.canton.platform.exception.ResourceNotFoundException;
import com.canton.platform.ledger.CantonLedgerSimulator;

/**
 * Manages KYC-gated custodial wallets. Mirrors DAML template
 * {@code Wallet.Wallet.Wallet} and its choices ({@code CreditAsset},
 * {@code DebitAsset}, {@code LockCollateral}, {@code ReleaseCollateral},
 * {@code CreditCash}, {@code DebitCash}): every mutation archives the
 * current contract and creates a new one, preserving the
 * immutable/contract-based state-transition model.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final CantonLedgerSimulator ledger;
    private final KycService kycService;

    /** walletId -> current Wallet contract id */
    private final ConcurrentHashMap<String, String> walletsById = new ConcurrentHashMap<>();

    public WalletService(CantonLedgerSimulator ledger, KycService kycService) {
        this.ledger = ledger;
        this.kycService = kycService;
    }

    /**
     * Opens a new custodial wallet. Mirrors
     * {@code WalletApplication.OpenWallet}: requires an approved KYC
     * record for {@code owner}.
     */
    public Wallet openWallet(String owner, String custodian, String regulator, String kycProvider, String walletId) {
        kycService.requireApproved(owner);
        if (walletsById.containsKey(walletId)) {
            throw new InsufficientBalanceException("Wallet with id " + walletId + " already exists");
        }
        Wallet wallet = Wallet.builder()
                .owner(owner)
                .custodian(custodian)
                .regulator(regulator)
                .kycProvider(kycProvider)
                .walletId(walletId)
                .holdings(List.of())
                .cashBalances(List.of())
                .build();
        String cid = ledger.create(TemplateIds.WALLET, wallet, Set.of(owner, custodian), Set.of(regulator));
        walletsById.put(walletId, cid);
        log.info("Opened wallet {} for owner={} custodian={}", walletId, owner, custodian);
        return wallet;
    }

    public Wallet getWallet(String walletId) {
        String cid = walletsById.get(walletId);
        if (cid == null) {
            throw new ResourceNotFoundException("No wallet found with id " + walletId);
        }
        return ledger.fetch(cid, TemplateIds.WALLET, Wallet.class);
    }

    public List<Wallet> getWalletsForParty(String party) {
        return ledger.<Wallet>queryAllActive(TemplateIds.WALLET).stream()
                .map(c -> c.payload())
                .filter(w -> w.owner().equals(party) || w.custodian().equals(party) || w.regulator().equals(party))
                .toList();
    }

    /** Mirrors {@code Wallet.CreditAsset}. */
    public synchronized Wallet creditAsset(String walletId, AssetClass assetClass, String instrumentId, BigDecimal quantity) {
        Wallet wallet = getWallet(walletId);
        List<WalletHolding> holdings = new java.util.ArrayList<>(wallet.holdings());
        var existing = holdings.stream().filter(h -> h.instrumentId().equals(instrumentId) && !h.locked()).findFirst();
        if (existing.isPresent()) {
            WalletHolding h = existing.get();
            holdings.remove(h);
            holdings.add(h.withQuantity(h.quantity().add(quantity)));
        } else {
            holdings.add(WalletHolding.builder().assetClass(assetClass).instrumentId(instrumentId).quantity(quantity).locked(false).build());
        }
        return replace(walletId, wallet.withHoldings(holdings));
    }

    /** Mirrors {@code Wallet.DebitAsset}. */
    public synchronized Wallet debitAsset(String walletId, String instrumentId, BigDecimal quantity) {
        Wallet wallet = getWallet(walletId);
        List<WalletHolding> holdings = new java.util.ArrayList<>(wallet.holdings());
        var existing = holdings.stream().filter(h -> h.instrumentId().equals(instrumentId) && !h.locked()).findFirst()
                .orElseThrow(() -> new InsufficientBalanceException("No unlocked holding for instrument " + instrumentId + " in wallet " + walletId));
        if (existing.get().quantity().compareTo(quantity) < 0) {
            throw new InsufficientBalanceException("Insufficient unlocked balance of " + instrumentId + " in wallet " + walletId);
        }
        holdings.remove(existing.get());
        BigDecimal remaining = existing.get().quantity().subtract(quantity);
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            holdings.add(existing.get().withQuantity(remaining));
        }
        return replace(walletId, wallet.withHoldings(holdings));
    }

    /** Mirrors {@code Wallet.LockCollateral}. */
    public synchronized Wallet lockCollateral(String walletId, AssetClass assetClass, String instrumentId, BigDecimal quantity) {
        Wallet wallet = getWallet(walletId);
        List<WalletHolding> holdings = new java.util.ArrayList<>(wallet.holdings());
        var existing = holdings.stream().filter(h -> h.instrumentId().equals(instrumentId) && !h.locked()).findFirst()
                .orElseThrow(() -> new InsufficientBalanceException("No unlocked holding for instrument " + instrumentId + " in wallet " + walletId));
        if (existing.get().quantity().compareTo(quantity) < 0) {
            throw new InsufficientBalanceException("Insufficient unlocked balance to lock for " + instrumentId);
        }
        holdings.remove(existing.get());
        BigDecimal remaining = existing.get().quantity().subtract(quantity);
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            holdings.add(existing.get().withQuantity(remaining));
        }
        var lockedExisting = holdings.stream().filter(h -> h.instrumentId().equals(instrumentId) && h.locked()).findFirst();
        if (lockedExisting.isPresent()) {
            WalletHolding lh = lockedExisting.get();
            holdings.remove(lh);
            holdings.add(lh.withQuantity(lh.quantity().add(quantity)));
        } else {
            holdings.add(WalletHolding.builder().assetClass(assetClass).instrumentId(instrumentId).quantity(quantity).locked(true).build());
        }
        return replace(walletId, wallet.withHoldings(holdings));
    }

    /** Mirrors {@code Wallet.ReleaseCollateral}. */
    public synchronized Wallet releaseCollateral(String walletId, String instrumentId, BigDecimal quantity) {
        Wallet wallet = getWallet(walletId);
        List<WalletHolding> holdings = new java.util.ArrayList<>(wallet.holdings());
        var existing = holdings.stream().filter(h -> h.instrumentId().equals(instrumentId) && h.locked()).findFirst()
                .orElseThrow(() -> new InsufficientBalanceException("No locked holding for instrument " + instrumentId + " in wallet " + walletId));
        if (existing.get().quantity().compareTo(quantity) < 0) {
            throw new InsufficientBalanceException("Insufficient locked balance to release for " + instrumentId);
        }
        holdings.remove(existing.get());
        BigDecimal remainingLocked = existing.get().quantity().subtract(quantity);
        if (remainingLocked.compareTo(BigDecimal.ZERO) > 0) {
            holdings.add(existing.get().withQuantity(remainingLocked));
        }
        var unlockedExisting = holdings.stream().filter(h -> h.instrumentId().equals(instrumentId) && !h.locked()).findFirst();
        if (unlockedExisting.isPresent()) {
            WalletHolding uh = unlockedExisting.get();
            holdings.remove(uh);
            holdings.add(uh.withQuantity(uh.quantity().add(quantity)));
        } else {
            holdings.add(WalletHolding.builder().assetClass(existing.get().assetClass()).instrumentId(instrumentId).quantity(quantity).locked(false).build());
        }
        return replace(walletId, wallet.withHoldings(holdings));
    }

    /** Mirrors {@code Wallet.CreditCash}. */
    public synchronized Wallet creditCash(String walletId, Money amount) {
        Wallet wallet = getWallet(walletId);
        List<Money> balances = new java.util.ArrayList<>(wallet.cashBalances());
        var existing = balances.stream().filter(m -> m.currency().equals(amount.currency())).findFirst();
        if (existing.isPresent()) {
            Money m = existing.get();
            balances.remove(m);
            balances.add(m.add(amount));
        } else {
            balances.add(amount);
        }
        return replace(walletId, wallet.withCashBalances(balances));
    }

    /** Mirrors {@code Wallet.DebitCash}. */
    public synchronized Wallet debitCash(String walletId, Money amount) {
        Wallet wallet = getWallet(walletId);
        List<Money> balances = new java.util.ArrayList<>(wallet.cashBalances());
        var existing = balances.stream().filter(m -> m.currency().equals(amount.currency())).findFirst()
                .orElseThrow(() -> new InsufficientBalanceException("No cash balance in currency " + amount.currency() + " for wallet " + walletId));
        if (existing.get().amount().compareTo(amount.amount()) < 0) {
            throw new InsufficientBalanceException("Insufficient cash balance in wallet " + walletId);
        }
        balances.remove(existing.get());
        balances.add(existing.get().subtract(amount));
        return replace(walletId, wallet.withCashBalances(balances));
    }

    private Wallet replace(String walletId, Wallet updated) {
        String oldCid = walletsById.get(walletId);
        ledger.archive(oldCid);
        String newCid = ledger.create(TemplateIds.WALLET, updated,
                Set.of(updated.owner(), updated.custodian()), Set.of(updated.regulator()));
        walletsById.put(walletId, newCid);
        return updated;
    }
}
