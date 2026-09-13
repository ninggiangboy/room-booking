package dev.ngb.backend.model;

/**
 * Which environment produced an arrival.
 *
 * <p>Test and development traffic is labelled at the boundary so it can be excluded from metrics
 * rather than filtered by guesswork later.</p>
 */
public enum ProducerEnvironment {

    /** Real traffic. */
    PRODUCTION,

    /** Pre-production. */
    STAGING,

    /** Developer machines. */
    DEVELOPMENT,

    /** Automated tests. */
    TEST
}
