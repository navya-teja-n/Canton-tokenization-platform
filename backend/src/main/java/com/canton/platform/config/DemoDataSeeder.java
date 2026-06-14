package com.canton.platform.config;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.canton.platform.domain.Money;
import com.canton.platform.domain.contracts.RepoProposal.RepoDirection;
import com.canton.platform.domain.enums.AssetClass;
import com.canton.platform.service.AssetIssuanceService;
import com.canton.platform.service.KycService;
import com.canton.platform.service.RepoService;
import com.canton.platform.service.TradeService;
import com.canton.platform.service.WalletService;

/**
 * Populates the in-memory ledger with a realistic reference portfolio on
 * application startup.
 *
 * <p>The platform's ledger ({@code CantonLedgerSimulator}) is in-memory and
 * is reset whenever the process restarts (e.g. after a Render free-tier
 * spin-down/wake cycle). Without seed data the dashboard would be empty
 * after every restart, which is misleading for anyone reviewing the
 * platform. This component recreates a small, internally-consistent set of
 * KYC approvals, custodial wallets, tokenized instruments, cash deposits,
 * and a pending trade/repo so the UI always reflects a live, working
 * workflow.
 *
 * <p>Disable by setting {@code platform.seed.enabled=false}.
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "platform.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String REGULATOR = "RegulatorFed";
    private static final String KYC_PROVIDER = "KycProviderInc";
    private static final String CUSTODIAN = "CustodianX";

    private final KycService kycService;
    private final WalletService walletService;
    private final AssetIssuanceService assetIssuanceService;
    private final TradeService tradeService;
    private final RepoService repoService;

    public DemoDataSeeder(KycService kycService,
                           WalletService walletService,
                           AssetIssuanceService assetIssuanceService,
                           TradeService tradeService,
                           RepoService repoService) {
        this.kycService = kycService;
        this.walletService = walletService;
        this.assetIssuanceService = assetIssuanceService;
        this.tradeService = tradeService;
        this.repoService = repoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seedKyc();
            seedWallets();
            seedInstruments();
            seedCashDeposits();
            seedPendingTrade();
            seedPendingRepo();
            log.info("Seed data loaded: 4 KYC approvals, 4 wallets, 3 instruments, "
                    + "4 cash deposits, 1 pending trade, 1 pending repo.");
        } catch (Exception e) {
            // Never let seeding failures prevent the application from
            // starting -- the platform is fully usable without seed data.
            log.error("Demo data seeding failed; the application will start with an empty ledger. Cause: {}",
                    e.getMessage(), e);
        }
    }

    private void seedKyc() {
        kycService.approve("BankA", KYC_PROVIDER, REGULATOR,
                "Alpha National Bank", "US", "LOW", "seed-kyc-banka");
        kycService.approve("BankB", KYC_PROVIDER, REGULATOR,
                "Beta Trust Bank", "US", "LOW", "seed-kyc-bankb");
        kycService.approve("InvestorAlice", KYC_PROVIDER, REGULATOR,
                "Alice Investments LLC", "US", "MEDIUM", "seed-kyc-alice");
        kycService.approve("InvestorBob", KYC_PROVIDER, REGULATOR,
                "Bob Capital Partners", "GB", "MEDIUM", "seed-kyc-bob");
    }

    private void seedWallets() {
        walletService.openWallet("BankA", CUSTODIAN, REGULATOR, KYC_PROVIDER, "WALLET-BANKA");
        walletService.openWallet("BankB", CUSTODIAN, REGULATOR, KYC_PROVIDER, "WALLET-BANKB");
        walletService.openWallet("InvestorAlice", CUSTODIAN, REGULATOR, KYC_PROVIDER, "WALLET-ALICE");
        walletService.openWallet("InvestorBob", CUSTODIAN, REGULATOR, KYC_PROVIDER, "WALLET-BOB");
    }

    private void seedInstruments() {
        // 10Y US Treasury bond, issued by BankA, held by InvestorAlice.
        assetIssuanceService.issueAsset("BankA", "InvestorAlice", CUSTODIAN, REGULATOR, "WALLET-ALICE",
                AssetClass.GOVERNMENT_BOND, "UST-10Y-2034", "US91282CJL71", "USD",
                new BigDecimal("1000"), new BigDecimal("500"),
                new BigDecimal("4.25"), new BigDecimal("985.50"),
                LocalDate.of(2024, 6, 14), LocalDate.of(2034, 6, 14));

        // 5Y US Treasury bond, issued by BankA and retained on its own
        // wallet -- used as collateral in the seeded repo below.
        assetIssuanceService.issueAsset("BankA", "BankA", CUSTODIAN, REGULATOR, "WALLET-BANKA",
                AssetClass.GOVERNMENT_BOND, "UST-5Y-2030", "US91282CJY81", "USD",
                new BigDecimal("1000"), new BigDecimal("1000"),
                new BigDecimal("3.75"), new BigDecimal("992.00"),
                LocalDate.of(2025, 6, 14), LocalDate.of(2030, 6, 14));

        // 26-week Treasury bill, issued by BankB, held by InvestorBob.
        assetIssuanceService.issueAsset("BankB", "InvestorBob", CUSTODIAN, REGULATOR, "WALLET-BOB",
                AssetClass.TREASURY_BILL, "TBILL-2026-12", "US912796XJ40", "USD",
                new BigDecimal("100"), new BigDecimal("10000"),
                BigDecimal.ZERO, new BigDecimal("98.75"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 1));
    }

    private void seedCashDeposits() {
        assetIssuanceService.issueDeposit("BankA", "InvestorAlice", REGULATOR, "WALLET-ALICE",
                "USD-CASH-ALICE-001", "USD", new BigDecimal("250000"));
        assetIssuanceService.issueDeposit("BankB", "InvestorBob", REGULATOR, "WALLET-BOB",
                "USD-CASH-BOB-001", "USD", new BigDecimal("750000"));
        assetIssuanceService.issueDeposit("BankA", "BankA", REGULATOR, "WALLET-BANKA",
                "USD-CASH-BANKA-001", "USD", new BigDecimal("5000000"));
        assetIssuanceService.issueDeposit("BankB", "BankB", REGULATOR, "WALLET-BANKB",
                "USD-CASH-BANKB-001", "USD", new BigDecimal("5000000"));
    }

    private void seedPendingTrade() {
        // InvestorAlice offers 100 units of her UST-10Y-2034 holding to
        // InvestorBob -- visible on the Trading Desk as a pending proposal.
        tradeService.proposeTrade("InvestorAlice", "InvestorBob", REGULATOR, "TRADE-DEMO-001",
                AssetClass.GOVERNMENT_BOND, "UST-10Y-2034", new BigDecimal("100"),
                new Money("USD", new BigDecimal("98550.00")));
    }

    private void seedPendingRepo() {
        // BankA borrows cash from BankB against 500 units of its
        // UST-5Y-2030 holding -- visible on the Repo Desk as a pending
        // proposal.
        LocalDate start = LocalDate.now();
        repoService.proposeRepo("BankA", "BankB", REGULATOR, "REPO-DEMO-001",
                RepoDirection.REPO, "UST-5Y-2030", new BigDecimal("500"),
                new Money("USD", new BigDecimal("480000.00")), new BigDecimal("5.25"),
                start, start.plusDays(30));
    }
}
