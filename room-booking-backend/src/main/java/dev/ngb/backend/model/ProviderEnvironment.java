package dev.ngb.backend.model;

/**
 * Which provider environment an account addresses.
 *
 * <p>The environment is part of the account's identity rather than a deployment property, so a
 * sandbox credential cannot be promoted into production by changing configuration elsewhere.</p>
 */
public enum ProviderEnvironment {
    /** Provider test environment; no real money and no real obligations. */
    SANDBOX,
    /** Live provider environment. */
    PRODUCTION
}
