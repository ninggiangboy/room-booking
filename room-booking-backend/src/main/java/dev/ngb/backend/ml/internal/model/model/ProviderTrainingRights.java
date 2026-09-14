package dev.ngb.backend.ml.internal.model.model;

/**
 * Whether an external provider may train on the inputs it is sent.
 */
public enum ProviderTrainingRights {

    /** The provider may not train on what it is sent. */
    PROHIBITED,

    /** The provider may, which bars every consequential use. */
    PERMITTED_WITH_APPROVAL
}
