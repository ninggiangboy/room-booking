package dev.ngb.backend.ml.internal.model.model;

/**
 * How consequential a model's advice is, and therefore who must approve it.
 *
 * <p>Anything other than ADVISORY requires risk, privacy and security review alongside the
 * consuming domain, and may not send its inputs to a provider permitted to train on them.</p>
 */
public enum ModelImpactClass {

    /** Informs a decision the domain would make anyway. */
    ADVISORY,

    /** Bears on physical safety or severe harm. */
    SAFETY,

    /** Bears on money moving, or on who is owed it. */
    FINANCIAL,

    /** Bears on what somebody is charged or paid. */
    PRICING,

    /** Bears on whether somebody may use part of the marketplace. */
    ELIGIBILITY,

    /** Bears on whether content or an actor stays visible. */
    MODERATION
}
