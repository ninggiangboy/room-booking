package dev.ngb.backend.support.internal.model.case_;

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

import dev.ngb.backend.support.internal.model.CaseSensitivityClass;


/**
 * What somebody said, attributed to them, kept as what it is.
 *
 * <p>An assertion is not a finding and must never be displayed as one: recording that a guest states the
 * lock was broken is not recording that the lock was broken.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_assertions")
public class CaseAssertion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** Which asserted by actor type this row carries. */
    private AssertionActorType assertedByActorType;
    /** The asserted by account holder this row belongs to. */
    private @Nullable UUID assertedByAccountHolderId;
    /** Reference to the asserted by external, held in its owning system rather than copied here. */
    private @Nullable String assertedByExternalReference;
    /** The on behalf of account holder this row belongs to. */
    private @Nullable UUID onBehalfOfAccountHolderId;
    /** Assertion subject. */
    private AssertionSubject assertionSubject;
    /** Where the statement itself is held; this domain stores the reference. */
    private String statementReference;
    /** Digest of the statement, so a later quotation can be checked against it. */
    private String statementHash;
    /** BCP 47 locale the content is written in. */
    private String locale;
    /** The source contact this row belongs to. */
    private @Nullable UUID sourceContactId;
    /** The source evidence this row belongs to. */
    private @Nullable UUID sourceEvidenceId;
    /** UTC instant asserted. */
    private Instant assertedAt;
    /** UTC instant received. */
    private Instant receivedAt;
    /** Which sensitivity class this row carries. */
    private CaseSensitivityClass sensitivityClass;
    /** Which visibility scope this row carries. */
    private CaseLinkVisibility visibilityScope;
    /** UTC instant withdrawn. */
    private @Nullable Instant withdrawnAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String withdrawalReason;
    /** The assertion this one corrects; the original is never edited. */
    private @Nullable UUID correctsAssertionId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
