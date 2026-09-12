package dev.ngb.backend.model;

/**
 * The kind of tax a rule, registration, or line concerns.
 *
 * <p>Several can apply to one night at once — a national VAT and a municipal lodging tax are separate
 * obligations to separate authorities — which is why tax is recorded per line rather than as a single
 * figure per booking.</p>
 */
public enum TaxType {
    /** Value added tax. */
    VAT,
    /** Goods and services tax. */
    GST,
    /** Sales tax levied on the transaction. */
    SALES_TAX,
    /** A tax specific to short-term accommodation. */
    LODGING_TAX,
    /** A levy on visitors, often per person per night. */
    TOURIST_TAX,
    /** Tax on the income the stay produces. */
    INCOME_TAX,
    /** Tax withheld at source from a party's proceeds. */
    WITHHOLDING_TAX,
    /** A municipal charge distinct from national tax. */
    CITY_TAX
}
