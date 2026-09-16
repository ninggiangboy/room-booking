package dev.ngb.backend.identity.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.ngb.backend.config.ApiErrorResponse;
import dev.ngb.backend.identity.internal.service.mfa.MfaService;

/**
 * Exposes TOTP enrollment and step-up verification over HTTP.
 *
 * <p>{@code @RestController} registers the class; {@code @RequestMapping} supplies the common URL
 * prefix under the current user's own resources, matching {@code UserController}'s
 * {@code /me} convention.</p>
 */
@RestController
@RequestMapping("/api/v1/users/me/mfa")
@RequiredArgsConstructor
@Tag(name = "Multi-factor authentication", description = "TOTP enrollment and step-up verification.")
public class MfaController {

    private final MfaService mfaService;
    private final Clock clock;

    /**
     * Enrolls a new TOTP factor for the authenticated account.
     *
     * @param userId authenticated account identifier
     * @return the seed and provisioning URI, shown exactly once
     */
    @PostMapping("/totp")
    @Operation(summary = "Enroll a TOTP factor", description = "Generates a new TOTP seed and returns it and its otpauth:// provisioning URI exactly once.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Factor enrolled", content = @Content(schema = @Schema(implementation = EnrollTotpResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/UserNotFound"),
            @ApiResponse(responseCode = "409", description = "An active TOTP credential already exists (TOTP_ALREADY_ENROLLED)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<EnrollTotpResponse> enrollTotp(@AuthenticationPrincipal UUID userId) {
        MfaService.EnrolledTotp enrolled = mfaService.enrollTotp(userId, clock.instant());
        return ResponseEntity.status(201).body(EnrollTotpResponse.from(enrolled));
    }

    /**
     * Disables the authenticated account's active TOTP factor.
     *
     * @param userId authenticated account identifier
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/totp")
    @Operation(summary = "Disable the TOTP factor", description = "Disables the caller's active TOTP credential.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Factor disabled"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "No active TOTP credential is enrolled (NO_ACTIVE_TOTP_CREDENTIAL)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> disableTotp(@AuthenticationPrincipal UUID userId) {
        mfaService.disableTotp(userId, clock.instant());
        return ResponseEntity.noContent().build();
    }

    /**
     * Verifies a TOTP code and issues a short-lived step-up proof.
     *
     * @param userId authenticated account identifier
     * @param request code from the holder's authenticator app
     * @return the proof to present to the sensitive action being stepped up to
     */
    @PostMapping("/totp/step-up")
    @Operation(summary = "Step up with a TOTP code", description = "Verifies a code against the caller's active TOTP factor and, on success, issues a short-lived proof no workflow in this codebase requires yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Step-up proof issued", content = @Content(schema = @Schema(implementation = StepUpResponse.class))),
            @ApiResponse(responseCode = "400", description = "Code is invalid or expired (INVALID_TOTP_CODE or VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "No active TOTP credential is enrolled (NO_ACTIVE_TOTP_CREDENTIAL)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public StepUpResponse stepUp(
            @AuthenticationPrincipal UUID userId, @Valid @RequestBody VerifyTotpCodeRequest request) {
        String proof = mfaService.stepUp(userId, request.code(), clock.instant());
        return new StepUpResponse(proof);
    }
}
