package dev.ngb.backend.hostops.internal.model.advice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import dev.ngb.backend.platform.JsonDocument;

/**
 * What a host should expect to receive, before the money exists.
 * <p>It is not a statement and not a promise: it names the basis it was computed on, carries a
 * validity horizon, and its net has to equal the components it is explained by. A net figure that
 * does not fall out of the lines beside it is the number a host will quote back during a dispute
 * with nothing able to reproduce it. Migration 022 owns the statement that is binding.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_payout_previews")
public class HostPayoutPreview {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The host the preview is for. */
    private UUID hostAccountHolderId;
    /** Whether the preview is about one booking or a period of them. */
    private PayoutPreviewSubject subjectKind;
    /** The booking being previewed. */
    private @Nullable UUID bookingId;
    /** First civil day the preview covers. */
    private @Nullable LocalDate periodStart;
    /** Day after the last civil day the preview covers. */
    private @Nullable LocalDate periodEnd;
    /** ISO 4217 alphabetic code every amount on this row is denominated in. */
    private String currency;
    /** Accommodation revenue before anything is taken off, in integer minor units. */
    private long grossAccommodationMinor;
    /** Guest-paid fees that reach the host, in integer minor units. */
    private long grossFeesMinor;
    /** Discounts the host funds, in integer minor units. */
    private long discountsMinor;
    /** The platforms fee, in integer minor units. */
    private long platformFeeMinor;
    /** Payment processing cost borne by the host, in integer minor units. */
    private long processingFeeMinor;
    /** Tax withheld at source, in integer minor units. */
    private long taxWithheldMinor;
    /** Amount held back into the hosts reserve, in integer minor units. */
    private long reserveRetainedMinor;
    /** Everything else, carrying its own sign, in integer minor units. */
    private long adjustmentsMinor;
    /** What the host should expect to receive, which must equal the components above. */
    private long estimatedNetMinor;
    /** Whether the preview rests on obligations already confirmed, on a forecast, or on both. */
    private PayoutPreviewBasis basis;
    /** What the preview assumes, in the words the host is shown. */
    private String assumptionSummary;
    /** The assumptions in structured form, for the screen that explains the number line by line. */
    private @Nullable JsonDocument assumptions;
    /** Earliest civil day the funds could be released under policy. */
    private @Nullable LocalDate earliestReleaseDate;
    /** Civil day the payout is expected to be made. */
    private @Nullable LocalDate expectedPayoutDate;
    /** UTC instant the preview was computed. */
    private Instant computedAt;
    /**
     * UTC instant after which the preview is stale; an estimate with no expiry becomes a quotation.
     */
    private Instant validUntil;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
