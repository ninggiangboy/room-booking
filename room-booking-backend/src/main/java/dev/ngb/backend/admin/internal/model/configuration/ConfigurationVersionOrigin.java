package dev.ngb.backend.admin.internal.model.configuration;

/**
 * How a configured value came to exist.
 * <p>Only a platform bootstrap and a migration exist without a request behind them, and neither is
 * permitted on a setting whose schema requires two people to agree.</p>
 */
public enum ConfigurationVersionOrigin {

    /** The seed value a setting was created with. */
    BOOTSTRAP,

    /** Applied under an approved request. */
    CHANGE_REQUEST,

    /** Restored an earlier value under an approved request. */
    ROLLBACK,

    /** Set by pulling an emergency switch. */
    KILL_SWITCH,

    /** Written by a schema migration rather than by anybody. */
    MIGRATION
}
