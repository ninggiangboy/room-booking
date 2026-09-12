package dev.ngb.backend.model;

/**
 * Where an account sits in the accounting equation.
 *
 * <p>The class decides how a balance is read and which report it rolls into. It is finance policy,
 * not a presentation choice, so it is fixed on the account rather than inferred from the code.</p>
 */
public enum LedgerAccountClass {
    /** Something the platform holds or is owed, such as provider clearing or bank cash. */
    ASSET,
    /** Something the platform owes, such as host payable, guest funds, or tax payable. */
    LIABILITY,
    /** Residual interest after liabilities. */
    EQUITY,
    /** Value the platform earned, such as commission or guest service fees. */
    REVENUE,
    /** Value the platform consumed, such as processor fees or chargeback losses. */
    EXPENSE,
    /** A reduction of revenue, such as platform-funded promotion discount. */
    CONTRA_REVENUE
}
