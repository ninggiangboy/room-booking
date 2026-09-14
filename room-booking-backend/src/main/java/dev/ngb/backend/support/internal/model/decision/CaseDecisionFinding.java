package dev.ngb.backend.support.internal.model.decision;

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
 * Which finding a decision rested on, and in what role.
 *
 * <p>Keeping this explicit is what lets an appeal argue with the reasoning rather than only the outcome.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_decision_findings")
public class CaseDecisionFinding {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The case decision this row belongs to. */
    private UUID caseDecisionId;
    /** The case finding this row belongs to. */
    private UUID caseFindingId;
    /** Which citation role this row carries. */
    private FindingCitationRole citationRole;
    /** Weight. */
    private FindingCitationWeight weight;
    /** Finding outcome at citation. */
    private FindingOutcome findingOutcomeAtCitation;
    /** Reference to the note, held in its owning system rather than copied here. */
    private @Nullable String noteReference;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
