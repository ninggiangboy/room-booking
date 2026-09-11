package dev.ngb.backend.model;

/**
 * Lifecycle of a link between a provider resource and a platform aggregate.
 *
 * <p>Only one {@code ACTIVE} link may exist per provider account, resource type, and aggregate.
 * Detached and superseded rows are retained so a late provider callback naming an old external ID
 * can still be resolved instead of being silently dropped.</p>
 */
public enum ExternalReferenceLifecycle {
    /** The current link for this aggregate at this provider. */
    ACTIVE,
    /** Deliberately unlinked; the provider resource is no longer ours. */
    DETACHED,
    /** Replaced by a newer link, typically after a provider migration. */
    SUPERSEDED
}
