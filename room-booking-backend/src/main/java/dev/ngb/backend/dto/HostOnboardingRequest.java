package dev.ngb.backend.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Optional host-profile information supplied when an active user becomes a host.
 *
 * @param bio optional public biography; blank text is stored as {@code null}
 */
public record HostOnboardingRequest(
        @Size(max = 5000, message = "bio must be at most 5000 characters") @Nullable String bio) {
}
