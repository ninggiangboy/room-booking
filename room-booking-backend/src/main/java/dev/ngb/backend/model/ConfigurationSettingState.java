package dev.ngb.backend.model;

/**
 * Whether a configuration setting still resolves.
 */
public enum ConfigurationSettingState {

    /** Still resolves. */
    ACTIVE,

    /** No longer resolves and takes no new values; its history is kept. */
    RETIRED
}
