package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;


/**
 * Immutable status-change command submitted to the administrator account-status endpoint.
 *
 * @param status requested status; only {@code ACTIVE} and {@code SUSPENDED} are accepted
 * @param reasonCode stable reason recorded on the audit trail
 */
public record AdminUpdateAccountStatusRequest(
        @NotNull(message = "status must not be null")
        AccountHolderStatus status,
        @NotBlank(message = "reasonCode must not be blank")
        String reasonCode) {
}
