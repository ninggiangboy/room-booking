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
 * An internal working note, deliberately a different table from {@code case_contacts}.
 *
 * <p>A note is never automatically disclosed as a participant message; a later lawful disclosure is
 * recorded rather than changing the note's own visibility in place.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_notes")
public class CaseNote {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Lifecycle episode. */
    private short lifecycleEpisode;
    /** Which note kind this row carries. */
    private CaseNoteKind noteKind;
    /** Which author actor type this row carries. */
    private NoteAuthorType authorActorType;
    /** The author account holder this row belongs to. */
    private @Nullable UUID authorAccountHolderId;
    /** Reference to the content, held in its owning system rather than copied here. */
    private String contentReference;
    /** Digest of the note, so a later quotation can be checked against it. */
    private String contentHash;
    /** BCP 47 locale the content is written in. */
    private @Nullable String locale;
    /** Which sensitivity class this row carries. */
    private CaseSensitivityClass sensitivityClass;
    /** Which visibility scope this row carries. */
    private NoteVisibility visibilityScope;
    /** Whether legal hold. */
    private boolean legalHold;
    /** UTC instant written. */
    private Instant writtenAt;
    /** The supersedes note this row belongs to. */
    private @Nullable UUID supersedesNoteId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
