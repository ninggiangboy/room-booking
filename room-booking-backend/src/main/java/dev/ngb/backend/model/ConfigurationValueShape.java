package dev.ngb.backend.model;

/**
 * What shape a configured value takes, which is the first thing validation checks.
 */
public enum ConfigurationValueShape {

    /** On or off. */
    BOOLEAN,

    /** A whole number. */
    INTEGER,

    /** A fractional number that is not money and not a share. */
    DECIMAL,

    /** Text. */
    STRING,

    /** An amount in integer minor units with a currency. */
    MONEY,

    /** A length of time. */
    DURATION,

    /** A share between zero and one. */
    PERCENTAGE,

    /** A structured value described by the schema. */
    OBJECT,

    /** An ordered collection described by the schema. */
    LIST
}
