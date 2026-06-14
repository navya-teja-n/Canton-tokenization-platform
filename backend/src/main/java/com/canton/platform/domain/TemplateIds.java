package com.canton.platform.domain;

/**
 * Canonical template identifiers used by {@link com.canton.platform.ledger.CantonLedgerSimulator},
 * formatted as {@code Module.SubModule:TemplateName} to mirror the DAML
 * module/template layout under {@code /daml}.
 */
public final class TemplateIds {
    private TemplateIds() {}

    public static final String KYC_REQUEST = "KYC.KycService:KycRequest";
    public static final String KYC_APPROVAL = "KYC.KycService:KycApproval";

    public static final String WALLET_APPLICATION = "Wallet.Wallet:WalletApplication";
    public static final String WALLET = "Wallet.Wallet:Wallet";

    public static final String TOKENIZED_ASSET = "Assets.Bond:TokenizedAsset";
    public static final String TOKENIZED_DEPOSIT = "Assets.Deposit:TokenizedDeposit";

    public static final String TRADE_PROPOSAL = "Trading.Trade:TradeProposal";
    public static final String EXECUTED_TRADE = "Trading.Trade:ExecutedTrade";

    public static final String REPO_PROPOSAL = "Repo.Repo:RepoProposal";
    public static final String REPO_AGREEMENT = "Repo.Repo:RepoAgreement";
    public static final String REPO_CLOSED_RECORD = "Repo.Repo:RepoClosedRecord";
}
