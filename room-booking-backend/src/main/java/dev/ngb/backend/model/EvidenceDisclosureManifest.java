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
 * An allowlisted package of evidence, frozen before it leaves.
 *
 * <p>Provider exports are not arbitrary case archives: the manifest fixes exactly which artifacts and
 * fields go, under which legal basis, to whom, until when, and with which digest.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("evidence_disclosure_manifests")
public class EvidenceDisclosureManifest {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** Which manifest kind this row carries. */
    private DisclosureManifestKind manifestKind;
    /** Which version of the manifest applies. */
    private int manifestVersion;
    /** Which recipient kind this row carries. */
    private DisclosureRecipientKind recipientKind;
    /** Reference to the recipient, held in its owning system rather than copied here. */
    private String recipientReference;
    /** The provider account this row belongs to. */
    private @Nullable UUID providerAccountId;
    /** Purpose. */
    private String purpose;
    /** Legal basis. */
    private String legalBasis;
    /** The minimization rules applied before freezing. */
    private String minimizationPolicyKey;
    /** Exactly which artifacts leave; the count is stored beside it. */
    private UUID[] evidenceItemIds;
    /** Which fields of those artifacts are approved to leave. */
    private String[] approvedFields;
    /** How many item there are. */
    private int itemCount;
    /** Digest over the frozen manifest, so what was sent can be proven. */
    private String manifestDigest;
    /** Where the state stands. */
    private DisclosureManifestState state;
    /** UTC instant frozen. */
    private @Nullable Instant frozenAt;
    /** The approved by account holder this row belongs to. */
    private @Nullable UUID approvedByAccountHolderId;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** UTC instant disclosed. */
    private @Nullable Instant disclosedAt;
    /** UTC instant access expires. */
    private @Nullable Instant accessExpiresAt;
    /** UTC instant revoked. */
    private @Nullable Instant revokedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String revocationReason;
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
