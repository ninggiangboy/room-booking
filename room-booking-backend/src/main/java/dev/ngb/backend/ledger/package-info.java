/**
 * Own double-entry accounting, host payable, payout, statement, and reconciliation as a module whose boundary against `payment` is preserved deliberately: `ledger` records what the business owes and has paid, `payment` records what a provider said happened to a transaction. Neither substitutes for the other.
 *
 * <p>This is a closed Spring Modulith application module: everything under {@code internal}
 * is invisible to every other module, and only the types at this root are a legitimate
 * cross-module dependency. See {@code docs/modules/ledger.md} for the full aggregate-cluster
 * map, public API, and rationale.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"payment :: types", "platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.ledger;
