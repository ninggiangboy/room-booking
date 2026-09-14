package dev.ngb.backend.ml.internal.model.label;

/**
 * What is known about one example's outcome.
 *
 * <p>The three states that are not an answer are the important ones. A stay that has not finished
 * has no satisfaction; a case nobody reviewed is not a safe case; a listing that was never rendered
 * is not a rejection. Collapsing any of them into NEGATIVE teaches a model the shape of the
 * previous system's coverage rather than the behaviour it was aimed at.</p>
 */
public enum LabelValueState {

    /** The outcome occurred inside the horizon. */
    POSITIVE,

    /** The outcome did not occur and the example stayed observable throughout. */
    NEGATIVE,

    /** The horizon has not closed, or the answer is not yet knowable. */
    UNRESOLVED,

    /** The example stopped being observable before the horizon closed. */
    CENSORED,

    /** Deliberately kept out of training, with the reason recorded. */
    EXCLUDED
}
