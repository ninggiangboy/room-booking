package dev.ngb.backend.model;

import java.time.Instant;
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

/**
 * The scope and the funding of an override programme, frozen.
 *
 * <p>Which geography, which dates, which evidence, whose money. Versioned separately from the
 * programme because an event's footprint changes as it unfolds.</p>
 *
 * <p>The funding split is exhaustive by constraint: a programme that funds sixty percent from the
 * platform and says nothing about the rest is how a host silently ends up paying for it.</p>
 *
 * <p>Versions are written once, so the row carries no optimistic lock and no update timestamp.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("policy_override_program_versions")
public class PolicyOverrideProgramVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Programme this version belongs to. */
    private UUID programId;
    /** Monotonic number within the programme. */
    private int versionNumber;
    /** Which bookings the programme may reach. */
    private JsonDocument scopeDocument;
    /** Start of the window a booking must fall in. */
    private Instant eligibilityFrom;
    /** End of that window. Open-ended while null. */
    private @Nullable Instant eligibilityTo;
    /** When applications stop being accepted. */
    private @Nullable Instant applicationWindowEndsAt;
    /** How much proof an applicant must supply. */
    private OverrideEvidenceRequirement evidenceRequirement;
    /** How long a decision may take. */
    private @Nullable Integer decisionSlaHours;
    /** Who pays, at the coarse level. */
    private OverrideFundingParty fundingParty;
    /** Guest share, in basis points. */
    private int guestFundedBasisPoints;
    /** Host share, in basis points. */
    private int hostFundedBasisPoints;
    /** Platform share, in basis points. The three must total 10 000. */
    private int platformFundedBasisPoints;
    /** ISO 4217 code, where a cap is set. */
    private @Nullable String currency;
    /** Total the programme may spend. */
    private @Nullable Long budgetCapMinor;
    /** Most it may spend on any one booking. */
    private @Nullable Long perBookingCapMinor;
    /** SHA-256 over the scope and funding, lowercase hex. */
    private String contentHash;
    /** Who approved the scope. */
    private @Nullable UUID approvedByActorId;
    /** When they did. */
    private @Nullable Instant approvedAt;
    /** When it became usable. Never before approval. */
    private @Nullable Instant publishedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether a booking falling at an instant is inside the eligibility window.
     *
     * @param at instant to test
     * @return {@code true} when the instant falls inside the window
     */
    public boolean coversInstant(Instant at) {
        return !at.isBefore(eligibilityFrom) && (eligibilityTo == null || at.isBefore(eligibilityTo));
    }
}
