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
 * One run of a host or beneficial owner against a sanctions, PEP, watch, age, or market rule.
 *
 * <p>A match is a question, not an answer. Names collide, so a {@code POTENTIAL_MATCH} must be
 * adjudicated by a named person before any capability decision reads it, and the row records who
 * adjudicated and what they concluded — that is precisely the decision a regulator will later ask
 * about.</p>
 *
 * <p>{@link #listVersion} is kept because a clear result is only meaningful against the list as it
 * stood: a host screened clear last year may match a list published since, which is why
 * {@link #nextScreeningDueAt} exists.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("screening_checks")
public class ScreeningCheck {

    /** Primary key of the screening run. */
    @Id
    private @Nullable UUID id;
    /** Legal profile screened. */
    private UUID hostLegalProfileId;
    /** Beneficial owner screened, when the subject was an owner rather than the host. */
    private @Nullable UUID beneficialOwnerId;
    /** Which list or rule was applied. */
    private ScreeningType screeningType;
    /** Provider account used for the run. */
    private @Nullable String providerAccountKey;
    /** Version of that provider account. */
    private @Nullable Short providerAccountVersion;
    /** Version of the list screened against; a clear result is only meaningful against one. */
    private @Nullable String listVersion;
    /** What the run returned. */
    private ScreeningResult result;
    /** How many candidate matches were returned; zero exactly when the result is clear. */
    private int matchCount;
    /** Reference to the provider's evidence in protected storage. */
    private @Nullable String evidenceReference;
    /** Operator who adjudicated the matches. */
    private @Nullable UUID adjudicatedBy;
    /** UTC instant of that adjudication; paired with {@link #adjudicatedBy}. */
    private @Nullable Instant adjudicatedAt;
    /** What the adjudicator concluded. */
    private @Nullable ScreeningAdjudication adjudicationOutcome;
    /** UTC instant the screening ran. */
    private Instant screenedAt;
    /** UTC instant by which the subject must be screened again. */
    private @Nullable Instant nextScreeningDueAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether this run leaves an unanswered question blocking eligibility.
     *
     * <p>An errored run blocks too: a screening that could not complete must never be read as one
     * that came back clear.</p>
     *
     * @return {@code true} when the run needs a person's adjudication before policy may rely on it
     */
    public boolean blocksEligibility() {
        if (result == ScreeningResult.ERROR) {
            return true;
        }
        boolean matched = result == ScreeningResult.POTENTIAL_MATCH
                || result == ScreeningResult.CONFIRMED_MATCH;
        return matched
                && (adjudicationOutcome == null
                        || adjudicationOutcome == ScreeningAdjudication.TRUE_MATCH
                        || adjudicationOutcome == ScreeningAdjudication.INCONCLUSIVE);
    }
}
