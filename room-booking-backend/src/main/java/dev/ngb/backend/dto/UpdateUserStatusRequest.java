package dev.ngb.backend.dto;

import dev.ngb.backend.model.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Administrative request to activate or suspend an account.
 *
 * @param status desired lifecycle status
 */
public record UpdateUserStatusRequest(
        @NotNull(message = "status must not be null") UserStatus status) {
}
