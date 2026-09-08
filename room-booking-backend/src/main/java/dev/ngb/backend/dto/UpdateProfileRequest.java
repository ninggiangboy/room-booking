package dev.ngb.backend.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Immutable partial-update request for the authenticated user's public profile.
 *
 * <p>Every component is optional and follows JSON Merge Patch semantics: an omitted or
 * {@code null} field leaves the stored value untouched. For the nullable columns
 * {@code avatarUrl} and {@code phoneNumber}, an empty or whitespace-only string clears the stored
 * value. {@code displayName} is required on the account, so a blank value is rejected by the
 * service rather than treated as a clear.</p>
 *
 * <p>{@code @Size} bounds the values at the HTTP boundary to the column widths, and
 * {@code @Pattern} rejects phone numbers that are not in E.164-like digit form. Normalization
 * (trimming, blank-to-{@code null}) happens in the service layer.</p>
 *
 * @param displayName optional replacement public name of at most 120 characters
 * @param avatarUrl optional replacement avatar location of at most 2048 characters; blank clears it
 * @param phoneNumber optional replacement phone number of at most 32 characters; blank clears it
 */
public record UpdateProfileRequest(
        @Size(max = 120, message = "displayName must not exceed 120 characters")
        @Nullable String displayName,
        @Size(max = 2048, message = "avatarUrl must not exceed 2048 characters")
        @Nullable String avatarUrl,
        @Size(max = 32, message = "phoneNumber must not exceed 32 characters")
        @Pattern(
                regexp = "\\s*|\\+?[0-9][0-9 .-]*",
                message = "phoneNumber must contain only digits, spaces, dots, hyphens, and an "
                        + "optional leading plus sign")
        @Nullable String phoneNumber) {
}
