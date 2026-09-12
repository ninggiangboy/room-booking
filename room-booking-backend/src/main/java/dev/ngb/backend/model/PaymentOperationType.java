package dev.ngb.backend.model;

/**
 * The external action one operation requests.
 *
 * <p>Every amount in this domain is stored unsigned; the type is what says which way the money
 * moves. A negative number in a money column is an invitation to read it the wrong way.</p>
 */
public enum PaymentOperationType {
    /** Reserve spending capacity without collecting. */
    AUTHORIZE,
    /** Convert an existing reservation into collected funds. */
    CAPTURE,
    /** Authorise and capture as one commercial action. */
    SALE,
    /** Release an unused reservation before settlement. */
    VOID,
    /** Return previously captured funds. */
    REFUND,
    /** Ask the provider for the current state of an object; moves no money. */
    QUERY
}
