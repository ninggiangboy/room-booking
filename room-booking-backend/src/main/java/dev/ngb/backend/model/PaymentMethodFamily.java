package dev.ngb.backend.model;

/**
 * Kind of instrument a payment is taken with.
 *
 * <p>The family, not the brand. Routing, refund capability, and customer-action requirements differ
 * by family; a Visa and a Mastercard differ only in ways the provider handles.</p>
 */
public enum PaymentMethodFamily {
    /** Payment card, present as a token rather than a number. */
    CARD,
    /** Wallet the guest approves outside the platform, such as a mobile wallet. */
    WALLET,
    /** Transfer the guest initiates at their own bank. */
    BANK_TRANSFER,
    /** Bank-hosted authorisation the guest is redirected into. */
    BANK_REDIRECT,
    /** Code the guest scans with a payment application. */
    QR_CODE,
    /** Pull from a bank account under a mandate. */
    DIRECT_DEBIT,
    /** Collected off-platform and recorded, never moved by a provider. */
    CASH
}
