package dev.ngb.backend.identity.internal.web;

/**
 * The short-lived proof issued after a successful step-up.
 *
 * @param stepUpProof opaque token to present to the sensitive action being stepped up to
 */
public record StepUpResponse(String stepUpProof) {
}
