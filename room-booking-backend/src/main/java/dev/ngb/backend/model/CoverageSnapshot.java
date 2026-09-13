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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The coverage that applied at the qualifying moment, frozen there.
 *
 * <p>Applying today's programme to a historical booking is the most tempting mistake in this area and
 * the one that produces promises the carrier never made.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("coverage_snapshots")
public class CoverageSnapshot {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The protection program version this row belongs to. */
    private UUID protectionProgramVersionId;
    /** The booking this row belongs to. */
    private UUID bookingId;
    /** The support case this row belongs to. */
    private @Nullable UUID supportCaseId;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** The covered account holder this row belongs to. */
    private UUID coveredAccountHolderId;
    /** Which moment fixed the terms, so today’s programme is never applied backwards. */
    private CoverageSnapshotBasis snapshotBasis;
    /** UTC instant snapshot. */
    private Instant snapshotAt;
    /** Coverage from. */
    private Instant coverageFrom;
    /** Coverage until. */
    private Instant coverageUntil;
    /** Coverage territory. */
    private String coverageTerritory;
    /** Limit amount, in integer minor units of its currency. */
    private long limitAmountMinor;
    /** ISO 4217 alphabetic code the limit are denominated in. */
    private String limitCurrency;
    /** Deductible amount, in integer minor units of its currency. */
    private long deductibleAmountMinor;
    /** Per item limit, in integer minor units of its currency. */
    private @Nullable Long perItemLimitMinor;
    /** How much of the limit has already been used, in minor units. */
    private long consumedAmountMinor;
    /** Reference to the exclusions, held in its owning system rather than copied here. */
    private String exclusionsReference;
    /** Determination. */
    private CoverageDetermination determination;
    /** Determination authority. */
    private AdjudicationAuthority determinationAuthority;
    /** The terms the determination rests on. */
    private @Nullable String citedTermsReference;
    /** The exclusions relied on, which a refusal must name. */
    private String[] citedExclusionCodes;
    /** The determined by account holder this row belongs to. */
    private @Nullable UUID determinedByAccountHolderId;
    /** UTC instant determined. */
    private @Nullable Instant determinedAt;
    /** Stable key naming the explanation template. */
    private @Nullable String explanationTemplateKey;
    /** Appeal route. */
    private @Nullable String appealRoute;
    /** UTC instant the determination stops being relied on. */
    private @Nullable Instant determinationExpiresAt;
    /** Reference to the consent, held in its owning system rather than copied here. */
    private @Nullable String consentReference;
    /** Which version of the disclosure applies. */
    private String disclosureVersion;
    /** Reference to the document, held in its owning system rather than copied here. */
    private @Nullable String documentReference;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
