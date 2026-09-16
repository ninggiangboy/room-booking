package dev.ngb.backend.identity;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable event marking that {@code capability_grants} gained a new row.
 *
 * <p>Carries no secret, so it is published through Spring Modulith's durable
 * {@code @ApplicationModuleListener} registry rather than a bare listener — see
 * {@code docs/architecture/event-publication-registry.md}. {@code granteeType} and {@code source}
 * are the grant's own enum names rather than {@code identity.internal}'s {@code PrincipalType}/
 * {@code GrantSource} types, since a public event's field types must themselves be consumable by
 * another module. A record fits an event because an event is a value that transports data and must
 * not change after publication.
 *
 * @param grantId identifier of the new {@code capability_grants} row
 * @param granteeType {@code PrincipalType} name of the principal receiving the grant
 * @param granteeId identifier of that principal
 * @param roleName role bundle label, or {@code null} for a resource-scoped delegation with none
 * @param source {@code GrantSource} name explaining why the grant exists
 * @param reasonCode stable reason the caller recorded for operator review
 * @param occurredAt the granting command's decision instant
 */
public record CapabilityGranted(
        UUID grantId,
        String granteeType,
        UUID granteeId,
        @Nullable String roleName,
        String source,
        String reasonCode,
        Instant occurredAt) {
}
