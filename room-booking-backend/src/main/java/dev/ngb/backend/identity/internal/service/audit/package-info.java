/**
 * Durable listeners that turn this module's own published facts into {@code audit_events} rows.
 *
 * <p>{@code IdentityFactAuditListener} is the first {@code @ApplicationModuleListener} in this
 * codebase — see {@code docs/architecture/event-publication-registry.md} — and exists so {@link
 * dev.ngb.backend.identity.AccountHolderCreated}, {@link dev.ngb.backend.identity.CapabilityGranted},
 * and {@link dev.ngb.backend.identity.SessionRevoked} are each backed by at least one registered
 * listener: Spring Modulith's {@code EventPublicationRegistry} only records a durable row per
 * {@code (event, listener)} pair, so an event with zero listeners leaves no trace at all. Recording
 * these facts here does not replace the higher-fidelity, actor-attributed audit entries {@code
 * AdminAccountService}, {@code UserController}, and {@code OrganizationService} already write
 * inline in the same transaction as the command that caused them; it adds a second, coarser trail —
 * one row per underlying write, attributed to {@link dev.ngb.backend.platform.ActorType#SYSTEM}
 * since the acting principal is not available by the time this listener runs — so a fact this
 * module publishes for {@code trust}/{@code admin} to react to is, from day one, at least as durable
 * as this module's own record of it.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.audit;
