package dev.ngb.backend.model;

/**
 * Role of one due component within a collection schedule.
 *
 * <p>Components exist so that an installment plan is not three attempts against one total. Each has
 * its own due date, state, and attempts.</p>
 */
public enum ScheduleComponentType {
    /** The whole amount, due at booking. */
    PAY_NOW,
    /** An up-front part that typically confirms the stay. */
    DEPOSIT,
    /** The remainder, due before or at arrival. */
    BALANCE,
    /** One of several equal parts of a payment plan. */
    INSTALLMENT
}
