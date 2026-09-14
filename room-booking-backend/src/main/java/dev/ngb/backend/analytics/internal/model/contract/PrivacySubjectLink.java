package dev.ngb.backend.analytics.internal.model.contract;

import java.math.BigDecimal;
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
 * A declared link between two pseudonymous subjects.
 *
 * <p>Authentication, explicit declaration and account merge are the only methods; there is no
 * device fingerprint, shared address or payment instrument, because a link the subject did not make
 * and cannot see is the covert identity the design forbids. Suppression is terminal.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("privacy_subject_links")
public class PrivacySubjectLink {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Pseudonym being linked from. */
    private String sourcePseudonym;
    /** What sort of subject the source pseudonym stands for. */
    private SubjectKind sourceSubjectKind;
    /** Pseudonym being linked to. */
    private String targetPseudonym;
    /** What sort of subject the target pseudonym stands for. */
    private SubjectKind targetSubjectKind;
    /** How the two came to be linked. */
    private SubjectLinkMethod linkMethod;
    /** How sure the link is, between zero and one; absent for authentication, which is certain. */
    private @Nullable BigDecimal confidence;
    /** The declared purpose the link may be used for, and only that one. */
    private String purpose;
    /** The basis on which the link is permitted. */
    private String legalBasis;
    /** The consent record behind it, required for a retroactive merge. */
    private @Nullable String consentReference;
    /**
     * Whether past anonymous behaviour may be joined to the account, which is a separate permission
     * from linking future behaviour.
     */
    private boolean retroactiveMergeAllowed;
    /** UTC instant the link takes effect. */
    private Instant effectiveFrom;
    /** UTC instant it ceases; open-ended when absent. */
    private @Nullable Instant effectiveTo;
    /** Where the link stands. */
    private SubjectLinkState state;
    /** UTC instant it was withdrawn. */
    private @Nullable Instant revokedAt;
    /** UTC instant it was suppressed; terminal and unrewritable. */
    private @Nullable Instant suppressedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String suppressionReason;
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
