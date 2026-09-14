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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;


/**
 * Who is on a case, in what role, and on what basis they are allowed to be there.
 *
 * <p>Representation is a consent decision with an expiry, not a permanent property of an account, and
 * visibility is per participant so attaching a second reporter cannot reveal the first one's evidence.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_participants")
public class CaseParticipant {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Which participant role this row carries. */
    private CaseParticipantRole participantRole;
    /** The account holder this row belongs to. */
    private @Nullable UUID accountHolderId;
    /** The outside party, when the participant is not one of our account holders. */
    private @Nullable String externalPartyReference;
    /** Which external party kind this row carries. */
    private @Nullable ExternalPartyKind externalPartyKind;
    /** On what basis this participant acts for somebody else. */
    private @Nullable RepresentationBasis representationBasis;
    /** The represented account holder this row belongs to. */
    private @Nullable UUID representedAccountHolderId;
    /** The captured consent behind a delegated representation. */
    private @Nullable String consentReference;
    /** UTC instant that consent stops. */
    private @Nullable Instant consentExpiresAt;
    /** What this participant may see, evaluated per participant. */
    private ParticipantVisibility visibilityScope;
    /** Contact preference. */
    private CaseContactPreference contactPreference;
    /** BCP 47 locale the content is written in. */
    private @Nullable String locale;
    /** Effective from. */
    private Instant effectiveFrom;
    /** Effective until. */
    private @Nullable Instant effectiveUntil;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String removalReason;
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
