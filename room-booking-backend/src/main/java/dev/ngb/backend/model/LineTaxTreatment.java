package dev.ngb.backend.model;

/**
 * How a line participates in tax.
 *
 * <p>Exempt, zero-rated, and out-of-scope are genuinely different and are not interchangeable: they
 * produce the same guest-facing amount but different filings, and some of them still have to be
 * reported to the authority.</p>
 */
public enum LineTaxTreatment {
    /** Tax applies at the jurisdiction's rate. */
    TAXABLE,
    /** Within scope but relieved of tax. */
    EXEMPT,
    /** Taxed at zero percent, which is still a taxable supply. */
    ZERO_RATED,
    /** Outside the jurisdiction's tax altogether. */
    OUT_OF_SCOPE,
    /** The line is itself a tax, so nothing is computed on it. */
    TAX_LINE
}
