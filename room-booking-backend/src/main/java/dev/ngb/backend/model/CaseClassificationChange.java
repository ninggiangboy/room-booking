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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One classification change, kept with the routing it caused.
 *
 * <p>Correcting a case from a refund request to a damage claim changes which policy, queue and deadline
 * applied; erasing the old value would erase the reason the first three days went the way they did.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_classification_history")
public class CaseClassificationChange {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Lifecycle episode. */
    private short lifecycleEpisode;
    /** Dimension. */
    private ClassificationDimension dimension;
    /** Prior value. */
    private @Nullable String priorValue;
    /** New value. */
    private String newValue;
    /** Which version of the taxonomy applies. */
    private int taxonomyVersion;
    /** Which changed by actor type this row carries. */
    private ClassificationActorType changedByActorType;
    /** The changed by account holder this row belongs to. */
    private @Nullable UUID changedByAccountHolderId;
    /** Approved reason code recording why; free text never stands in for one. */
    private String reasonCode;
    /** Reference to the evidence, held in its owning system rather than copied here. */
    private @Nullable String evidenceReference;
    /** Routing consequence. */
    private @Nullable String routingConsequence;
    /** The prior queue this row belongs to. */
    private @Nullable UUID priorQueueId;
    /** The new queue this row belongs to. */
    private @Nullable UUID newQueueId;
    /** UTC instant effective. */
    private Instant effectiveAt;
    /** UTC instant committed. */
    private Instant committedAt;
    /** The correlation this row belongs to. */
    private @Nullable UUID correlationId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
