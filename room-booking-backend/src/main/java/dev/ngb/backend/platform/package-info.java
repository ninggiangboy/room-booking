/**
 * The handful of concepts that belong to none of the 21 business modules: durable transport
 * primitives (idempotency, outbox, inbox, audit, external references) and the shared kernel of
 * value types and enums more than one module's aggregates need.
 *
 * <p>This is the one module every other module — and {@code config} — may depend on; it depends on
 * nothing else in the application. It is named as a {@code sharedModules} entry on
 * {@link dev.ngb.backend.RoomBookingBackendApplication @Modulithic}, since unlike every other module
 * it has no upstream/downstream direction of its own. See {@code docs/modules/platform.md} for the
 * R0–R3 rules that decide whether a type belongs here or with its natural owner instead — this
 * module is the destination of last resort, not the first guess, because a type that lives here
 * cannot change without touching every module that depends on it.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.platform;
