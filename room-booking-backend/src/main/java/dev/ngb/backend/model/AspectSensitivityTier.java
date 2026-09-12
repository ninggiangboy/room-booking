package dev.ngb.backend.model;

/**
 * How freely one aspect may be used.
 */
public enum AspectSensitivityTier {
    /** Usable anywhere this domain is authorized. */
    GENERAL,
    /** Usable only for named purposes. */
    RESTRICTED,
    /** Never an input to ordering, whatever the model would like. */
    PROHIBITED_FOR_RANKING
}
