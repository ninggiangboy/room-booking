package dev.ngb.backend.ml.internal.model.feature;

/**
 * Data type of a feature value, and therefore which column stores it.
 */
public enum FeatureDataType {

    /** True or false, stored as text. */
    BOOLEAN,

    /** Whole number, stored in the numeric column. */
    INTEGER,

    /** Fractional number, stored in the numeric column. */
    DECIMAL,

    /** One of the levels the definition declares, stored as text. */
    CATEGORICAL,

    /** A vector, held in analytical storage and referenced from here. */
    EMBEDDING,

    /** An instant, stored as text in its canonical form. */
    TIMESTAMP
}
