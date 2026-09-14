package dev.ngb.backend.trust.internal.model.content;

/**
 * What the platform concluded about a report.
 *
 * <p>A report is evidence that a complaint was made. This says what came of it, and is absent until
 * somebody actually decided.</p>
 */
public enum ReportAdjudication {
    /** The allegation was borne out. */
    SUBSTANTIATED,
    /** The allegation was not borne out. */
    UNSUBSTANTIATED,
    /** Not a matter this path decides. */
    OUT_OF_SCOPE,
    /** The same complaint was already being handled. */
    DUPLICATE,
    /** The report itself was an abuse of the reporting path. */
    ABUSIVE_REPORT;
}
