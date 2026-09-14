package dev.ngb.backend.trust.internal.model.label;

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
 * A post-event outcome, adjudicated, with the window it is true over.
 *
 * <p>Labels are not copied from adverse events. A decision to deny is not proof that denying was
 * right, so a model may not treat its own output as the answer it was graded against, and an
 * unverified report is not ground truth either -- both exclusions are check constraints. Only a
 * confirmed label with evidence behind it may be trained on, and it becomes usable strictly after the
 * outcome was observed, so the answer cannot be handed to a model at the moment it was asked. One live
 * label per subject, action and taxonomy: a reversal supersedes rather than coexisting, because two
 * contradictory answers in a training set are worse than neither.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_labels")
public class RiskLabel {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The vocabulary this label is written in. */
    private UUID riskLabelTaxonomyVersionId;
    /** Who or what the label is about. */
    private UUID subjectId;
    /** The action it concerns, where it concerns one. */
    private @Nullable String protectedAction;
    /** The value, drawn from the taxonomy. */
    private String labelValue;
    /** How confident the adjudication is. */
    private @Nullable BigDecimal confidence;
    /** Where the outcome came from. */
    private LabelSourceKind sourceKind;
    /** The outcome itself. */
    private @Nullable String sourceOutcomeReference;
    /** The evidence behind it; required to train on. */
    private @Nullable String evidenceReference;
    /** The decision this label relates to, for measurement rather than training. */
    private @Nullable UUID riskDecisionId;
    /** How settled the label is. */
    private LabelAdjudicationState adjudicationState;
    /** Who adjudicated it; required for a human adjudication. */
    private @Nullable UUID adjudicatedByAccountHolderId;
    /** Whether it may be used as ground truth. */
    private boolean trainingEligible;
    /** Start of the interval it describes. */
    private Instant applicableFrom;
    /** End of that interval. */
    private @Nullable Instant applicableUntil;
    /** When the outcome was observed. */
    private Instant observedAt;
    /** When it matures; always after it was observed. */
    private Instant availableForTrainingAt;
    /** The label this one corrects. */
    private @Nullable UUID supersedesLabelId;
    /** The label that corrected this one. */
    private @Nullable UUID supersededByLabelId;
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
