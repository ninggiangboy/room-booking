package dev.ngb.backend.analytics.internal.model.metric;

/**
 * How much authority a metric carries.
 *
 * <p>Alerting metrics may be approximate, product metrics may be restated as late facts arrive, and
 * audited financial figures follow reconciled close rules. Their labels have to differ visibly.</p>
 */
public enum MetricAuthorityClass {

    /** Low-latency and approximate, optimised for alerting. */
    OPERATIONAL,

    /** Governed and restatable as late facts arrive. */
    PRODUCT,

    /** Follows reconciled close rules and finance-owned definitions. */
    AUDITED_FINANCIAL
}
