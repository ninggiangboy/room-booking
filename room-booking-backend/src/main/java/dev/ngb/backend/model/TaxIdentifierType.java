package dev.ngb.backend.model;

/**
 * Kind of tax identifier a host has declared.
 *
 * <p>The type matters to more than storage: it decides which validation applies, whether withholding
 * is required, and what has to appear on an invoice.</p>
 */
public enum TaxIdentifierType {
    /** An individual's personal tax code. */
    PERSONAL_TAX_CODE,
    /** A company's tax code. */
    BUSINESS_TAX_CODE,
    /** A value-added-tax registration number. */
    VAT_NUMBER,
    /** A tax identifier issued outside the operating market. */
    FOREIGN_TIN
}
