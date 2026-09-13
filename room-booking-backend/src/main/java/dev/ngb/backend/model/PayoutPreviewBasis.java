package dev.ngb.backend.model;

/**
 * What a payout preview rests on. A preview over confirmed obligations is arithmetic; one over a
 * forecast is an estimate, and the host is told which they are reading.
 */
public enum PayoutPreviewBasis {

    /** Arithmetic over money already owed. */
    CONFIRMED_OBLIGATIONS,

    /** An estimate over nights that have not sold. */
    FORECAST,

    /** Part confirmed and part forecast. */
    MIXED
}
