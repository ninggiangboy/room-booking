package dev.ngb.backend.support.internal.model.remedy;

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
import dev.ngb.backend.platform.PolicyVersionStatus;

import dev.ngb.backend.support.internal.model.AdjudicationAuthority;

/**
 * The approved terms of a protection or insurance product, and the role the platform plays in it.
 *
 * <p>The declared role is stored so naming, disclosure and claims authority can be checked against it
 * rather than against marketing copy.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("protection_program_versions")
public class ProtectionProgramVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the program. */
    private String programKey;
    /** Which version of the program applies. */
    private int programVersion;
    /** The market this row belongs to. */
    private UUID marketId;
    /** The legal entity this row belongs to. */
    private @Nullable UUID legalEntityId;
    /** Where the status stands. */
    private PolicyVersionStatus status;
    /** Effective from. */
    private @Nullable Instant effectiveFrom;
    /** Effective until. */
    private @Nullable Instant effectiveUntil;
    /** Which platform role this row carries. */
    private ProtectionPlatformRole platformRole;
    /** The approved name, which must match the declared platform role. */
    private String displayNameKey;
    /** Covered party. */
    private CoveredParty coveredParty;
    /** Covered object. */
    private CoveredObject coveredObject;
    /** Coverage territory. */
    private String coverageTerritory;
    /** Coverage period rule. */
    private String coveragePeriodRule;
    /** Limit amount, in integer minor units of its currency. */
    private long limitAmountMinor;
    /** ISO 4217 alphabetic code the limit are denominated in. */
    private String limitCurrency;
    /** Deductible amount, in integer minor units of its currency. */
    private long deductibleAmountMinor;
    /** Per item limit, in integer minor units of its currency. */
    private @Nullable Long perItemLimitMinor;
    /** Where the exclusions are held. */
    private String exclusionsReference;
    /** Premium amount, in integer minor units of its currency. */
    private @Nullable Long premiumAmountMinor;
    /** ISO 4217 alphabetic code the premium are denominated in. */
    private @Nullable String premiumCurrency;
    /** The provider account this row belongs to. */
    private @Nullable UUID providerAccountId;
    /** Reference to the provider program, held in its owning system rather than copied here. */
    private @Nullable String providerProgramReference;
    /** Adjudication authority. */
    private AdjudicationAuthority adjudicationAuthority;
    /** How long, in days, the submission deadline runs. */
    private @Nullable Short submissionDeadlineDays;
    /** Whether consent required. */
    private boolean consentRequired;
    /** Which version of the disclosure applies. */
    private String disclosureVersion;
    /** Reference to the document, held in its owning system rather than copied here. */
    private @Nullable String documentReference;
    /** Complaint route. */
    private String complaintRoute;
    /** The approved by account holder this row belongs to. */
    private @Nullable UUID approvedByAccountHolderId;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** Digest of the content, so it can be shown later to be unchanged. */
    private String contentHash;
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
