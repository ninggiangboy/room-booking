package dev.ngb.backend.model;

/**
 * How a model's scores were calibrated into probabilities.
 */
public enum CalibrationMethod {

    /** Platt scaling. */
    PLATT_SCALING,

    /** Isotonic regression. */
    ISOTONIC_REGRESSION,

    /** Temperature scaling. */
    TEMPERATURE_SCALING,

    /** None. */
    NONE
}
