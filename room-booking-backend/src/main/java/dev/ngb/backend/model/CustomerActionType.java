package dev.ngb.backend.model;

/**
 * Kind of step the guest must complete away from the server request.
 *
 * <p>Stored with a deadline, always. An attempt waiting on a guest with no deadline holds inventory
 * that nobody will ever reclaim.</p>
 */
public enum CustomerActionType {
    /** The guest is sent to a provider or bank page. */
    REDIRECT,
    /** Card authentication under the 3-D Secure protocol. */
    THREE_D_SECURE,
    /** The guest approves the charge inside a wallet application. */
    WALLET_APPROVAL,
    /** The guest scans a code with a payment application. */
    QR_SCAN,
    /** The guest establishes a mandate for later collection. */
    MANDATE_SETUP
}
