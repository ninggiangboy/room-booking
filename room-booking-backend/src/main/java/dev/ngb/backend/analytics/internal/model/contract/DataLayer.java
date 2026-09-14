package dev.ngb.backend.analytics.internal.model.contract;

/**
 * Which layer of the analytical stack a dataset belongs to.
 *
 * <p>Raw does not mean ungoverned: encryption, access, retention and minimisation apply from
 * landing onward.</p>
 */
public enum DataLayer {

    /** Accepted envelopes and arrival attempts as they came in. */
    LANDING,

    /** Deduplicated facts with shared dimensions and standardised identifiers. */
    CONFORMED,

    /** Governed facts and dimensions at a declared grain. */
    SEMANTIC,

    /** Dashboards, approved research datasets, experiment analysis and model inputs. */
    SERVING
}
