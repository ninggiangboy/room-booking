package dev.ngb.backend.model;

/**
 * The transformation kind of {@code evidence_transformations}.
 */
public enum TransformationKind {

    /** Redaction. */
    REDACTION,

    /** Transcription. */
    TRANSCRIPTION,

    /** Translation. */
    TRANSLATION,

    /** Format conversion. */
    FORMAT_CONVERSION,

    /** Thumbnail. */
    THUMBNAIL,

    /** Metadata strip. */
    METADATA_STRIP,

    /** Export package. */
    EXPORT_PACKAGE,

    /** Ocr. */
    OCR
}
