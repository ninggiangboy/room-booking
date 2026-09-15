package dev.ngb.backend.trust.internal.model.intervention;

/**
 * What an appeal is against.
 *
 * <p>Exactly one target, and it must be the one the kind names, so every appeal has a single
 * decision it could supersede.</p>
 */
public enum AppealTargetKind {
    /** One risk decision. */
    RISK_DECISION,
    /** One restriction. */
    RISK_RESTRICTION,
    /** One moderation decision. */
    MODERATION_DECISION
}
