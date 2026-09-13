package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalDate;
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
 * What actually happened to one night, listing or rate plan in a bulk edit.
 * <p>One request against four hundred nights is four hundred outcomes, each of which the domain
 * that owns it may refuse: a night under an active claim, a price under a floor, a restriction the
 * market forbids. Every outcome other than applied carries a reason code the host interface can
 * translate.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_bulk_edit_targets")
public class HostBulkEditTarget {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The bulk edit this outcome belongs to. */
    private UUID bulkEditRequestId;
    /** Position of this target within its request, unique there. */
    private int sequenceNumber;
    /** What kind of thing the edit was attempted against. */
    private BulkEditTargetKind targetKind;
    /** The listing the edit was attempted against. */
    private @Nullable UUID listingId;
    /** The inventory resource the edit was attempted against. */
    private @Nullable UUID inventoryResourceId;
    /** The night the edit was attempted against. */
    private @Nullable LocalDate stayDate;
    /** What actually happened to this target. */
    private BulkEditTargetOutcome outcome;
    /** Approved reason code recording why the target was skipped or refused. */
    private @Nullable String reasonCode;
    /** What the owning domain said, for the host to read. */
    private @Nullable String reasonDetail;
    /** What the value was before the edit. */
    private @Nullable String beforeValue;
    /** What the value became. */
    private @Nullable String afterValue;
    /** UTC instant the edit was attempted against this target. */
    private Instant attemptedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
