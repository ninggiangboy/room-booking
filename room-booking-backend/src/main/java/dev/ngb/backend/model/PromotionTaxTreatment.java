package dev.ngb.backend.model;

/**
 * Whether a promotion reduces what is taxed, or is paid on top of it.
 *
 * <p>Not a modelling nicety. A discount that reduces the taxable base lowers the tax the authority is
 * owed; a rebate paid after tax does not. Choosing the wrong one under-remits or over-remits real
 * money to a real government.</p>
 */
public enum PromotionTaxTreatment {
    /** The discount lowers the amount on which tax is computed. */
    REDUCES_TAXABLE_BASE,
    /** Tax is computed on the undiscounted amount and the benefit is returned afterwards. */
    POST_TAX_REBATE,
    /** The benefit touches nothing taxable. */
    NOT_APPLICABLE
}
