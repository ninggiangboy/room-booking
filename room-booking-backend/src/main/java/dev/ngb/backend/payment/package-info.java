/**
 * Own provider-independent payment state — collection obligation, operation, webhook, refund execution, dispute gateway — as a module whose boundary against `ledger` is the single most important one in the whole marketplace, and never let that boundary blur.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/payment.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"booking :: types", "platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.payment;
