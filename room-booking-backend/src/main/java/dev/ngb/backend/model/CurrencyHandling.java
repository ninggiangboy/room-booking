package dev.ngb.backend.model;

/**
 * How a money metric deals with more than one currency.
 *
 * <p>There is no third option in which the amounts simply add up.</p>
 */
public enum CurrencyHandling {

    /** Each currency is reported separately and never summed across. */
    NATIVE_PARTITIONED,

    /** Converted through a named, finance-approved rate dataset at a stated time. */
    CONVERTED
}
