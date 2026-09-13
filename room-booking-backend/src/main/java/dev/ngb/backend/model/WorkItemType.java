package dev.ngb.backend.model;

/**
 * The work item type of {@code case_work_items}.
 */
public enum WorkItemType {

    /** Triage. */
    TRIAGE,

    /** Safety response. */
    SAFETY_RESPONSE,

    /** First response. */
    FIRST_RESPONSE,

    /** Evidence collection. */
    EVIDENCE_COLLECTION,

    /** Evidence review. */
    EVIDENCE_REVIEW,

    /** Respondent statement. */
    RESPONDENT_STATEMENT,

    /** Valuation. */
    VALUATION,

    /** Adjudication. */
    ADJUDICATION,

    /** Approval. */
    APPROVAL,

    /** Remedy execution. */
    REMEDY_EXECUTION,

    /** Provider submission. */
    PROVIDER_SUBMISSION,

    /** Provider query. */
    PROVIDER_QUERY,

    /** Reconciliation. */
    RECONCILIATION,

    /** Appeal review. */
    APPEAL_REVIEW,

    /** Quality review. */
    QUALITY_REVIEW,

    /** Closure check. */
    CLOSURE_CHECK
}
