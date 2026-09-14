package dev.ngb.backend.ml.internal.model.model;

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
 * One function's recorded decision on one model version.
 *
 * <p>Append-only, one standing decision per role per version, and never signed by the model's own
 * owner. The roles are separate because the questions are: whether the model works is a different
 * question from whether it is lawful to use, and both are different from whether the domain that
 * will act on it accepts the authority being handed over.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("model_approvals")
public class ModelApproval {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The version being decided on. */
    private UUID modelVersionId;
    /** Which function recorded this decision. */
    private ModelApprovalRole approvalRole;
    /** Who decided; never the model's own owner. */
    private String approverReference;
    /** Whether the function approved or rejected the version. */
    private ModelApprovalDecision decision;
    /** Why, in their own words. */
    private String decisionReason;
    /** The review record or document behind the decision. */
    private @Nullable String evidenceReference;
    /** Exactly what authority was granted, which a later promotion is checked against. */
    private String approvedScope;
    /** UTC instant the decision was recorded. */
    private Instant decidedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
