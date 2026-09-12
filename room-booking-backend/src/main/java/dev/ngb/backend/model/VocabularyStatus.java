package dev.ngb.backend.model;

/**
 * Whether a version of the amenity vocabulary may be used.
 *
 * <p>Vocabularies are retired rather than deleted, because listings and historical bookings cite
 * their terms: retiring a version must not orphan what it described.</p>
 */
public enum VocabularyStatus {
    /** Being prepared; not usable. */
    DRAFT,
    /** In use for new and edited supply. */
    ACTIVE,
    /** Superseded; retained so historical references still resolve. */
    RETIRED
}
