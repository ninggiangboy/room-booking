package dev.ngb.backend.model;

/**
 * Which pair of facts a reconciliation run compares.
 *
 * <p>Each layer has its own identifiers, coverage window, timing tolerance, and owner. One green
 * aggregate total at one layer cannot hide transaction-level duplicates or cross-host misallocation
 * at another, which is why the layer is recorded on the run.</p>
 */
public enum ReconciliationControlLayer {
    /** The booking's contracted value against what was asked of the guest. */
    SNAPSHOT_TO_OBLIGATION,
    /** What was owed against what was verifiably collected. */
    OBLIGATION_TO_CAPTURE,
    /** Verified payment facts against the entries they produced. */
    PAYMENT_TO_JOURNAL,
    /** The provider's report against the provider clearing account. */
    PROVIDER_REPORT_TO_CLEARING,
    /** The provider's settlement batch against the bank statement. */
    SETTLEMENT_TO_BANK,
    /** Host payable allocations against what payouts consumed. */
    PAYABLE_TO_PAYOUT_ITEMS,
    /** Payout provider evidence against payout-in-transit and bank cash. */
    PAYOUT_TO_IN_TRANSIT,
    /** Returned transfers against the recoveries and restored balances they caused. */
    RETURN_TO_RECOVERY,
    /** Tax payable and withholding against documents and filings. */
    TAX_TO_DOCUMENT,
    /** This subledger against a downstream general ledger. */
    SUBLEDGER_TO_GENERAL_LEDGER
}
