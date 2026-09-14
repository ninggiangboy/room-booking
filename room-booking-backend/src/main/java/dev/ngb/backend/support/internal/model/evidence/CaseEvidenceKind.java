package dev.ngb.backend.support.internal.model.evidence;

import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.messaging.internal.model.delivery.Message;
import dev.ngb.backend.stay.internal.model.incident.Incident;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.messaging.internal.model.delivery.Message;
import dev.ngb.backend.stay.internal.model.incident.Incident;
import dev.ngb.backend.supply.internal.model.listing.Listing;

/**
 * The evidence kind of {@code case_evidence_items}.
 */
public enum CaseEvidenceKind {

    /** Photo. */
    PHOTO,

    /** Video. */
    VIDEO,

    /** Document. */
    DOCUMENT,

    /** Receipt. */
    RECEIPT,

    /** Invoice. */
    INVOICE,

    /** Estimate. */
    ESTIMATE,

    /** Inspection report. */
    INSPECTION_REPORT,

    /** Message revision. */
    MESSAGE_REVISION,

    /** Review revision. */
    REVIEW_REVISION,

    /** Listing snapshot. */
    LISTING_SNAPSHOT,

    /** Booking snapshot. */
    BOOKING_SNAPSHOT,

    /** Payment artifact. */
    PAYMENT_ARTIFACT,

    /** Provider artifact. */
    PROVIDER_ARTIFACT,

    /** Access observation. */
    ACCESS_OBSERVATION,

    /** Incident record. */
    INCIDENT_RECORD,

    /** Call metadata. */
    CALL_METADATA,

    /** Agent note. */
    AGENT_NOTE,

    /** Third party report. */
    THIRD_PARTY_REPORT,

    /** Derivative. */
    DERIVATIVE
}
