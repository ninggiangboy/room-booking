package dev.ngb.backend.hostops.internal.model.advice;

/**
 * Who answered a piece of advice. Ignoring advice is an outcome the platform observes rather than
 * one a person takes, which is why automation is one of the values.
 */
public enum AdviceActorRole {

    /** The host the advice was shown to. */
    HOST,

    /** Somebody acting for the host, recorded as such rather than as the host. */
    CO_HOST,

    /** No person decided; the platform observed the advice lapse. */
    PLATFORM_AUTOMATION
}
