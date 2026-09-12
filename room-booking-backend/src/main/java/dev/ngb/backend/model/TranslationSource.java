package dev.ngb.backend.model;

/**
 * Who produced a piece of listing content in a given language.
 *
 * <p>Recorded because a machine translation and the host's own words carry different authority. When
 * a guest disputes what a listing promised, the source wording is what the host actually said.</p>
 */
public enum TranslationSource {
    /** Written by the host. */
    HOST,
    /** Produced by machine translation. */
    MACHINE,
    /** Produced by a human translator. */
    PROFESSIONAL
}
