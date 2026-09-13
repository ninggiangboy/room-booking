package dev.ngb.backend.model;

/**
 * Why two operator roles may not be held by one person.
 */
public enum RoleConflictBasis {

    /** One person would both make and check the same decision. */
    MAKER_CHECKER,

    /** One person would both move money and authorise the movement. */
    FINANCIAL_CONTROL,

    /** Together the two roles reach more personal data than either was approved for. */
    PRIVACY,

    /** One person would both report and adjudicate a safety matter. */
    SAFETY,

    /** A regulator requires the two duties to be held apart. */
    REGULATORY
}
