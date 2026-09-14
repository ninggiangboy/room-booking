package dev.ngb.backend.analytics.internal.model.arrival;

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
import dev.ngb.backend.platform.DataPrivacyClass;

import dev.ngb.backend.platform.DataPrivacyClass;


/**
 * The registered contract for one event name at one schema version.
 *
 * <p>An event moves DRAFT to REVIEWED to ACTIVE to DEPRECATED to RETIRED, and once it leaves draft
 * its meaning is frozen. Grain and meaning never change in place; a changed field is a new version,
 * which is what keeps historical envelopes readable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("event_definitions")
public class EventDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable event name, which never changes meaning in place. */
    private String eventName;
    /** Which schema version of this event name the row defines. */
    private short schemaVersion;
    /** Which event class this row carries. */
    private EventClass eventClass;
    /** The component that publishes it. */
    private String producer;
    /** What the event asserts, in words a consumer can act on. */
    private String description;
    /** What one event of this name represents. */
    private String grain;
    /** The source transaction that commits the fact; required for a domain fact. */
    private @Nullable String sourceTransaction;
    /** Digest of the schema, so it can be shown later to be unchanged. */
    private String schemaDigest;
    /** Reference to the schema, held in its owning system rather than copied here. */
    private String schemaReference;
    /** A worked example envelope, required before activation. */
    private @Nullable String exampleReference;
    /** What ordering guarantee, if any, consumers may rely on. */
    private OrderingSemantics orderingSemantics;
    /** What kind of schema change is permitted within this version. */
    private SchemaCompatibilityPolicy compatibilityPolicy;
    /** Which privacy class this row carries. */
    private DataPrivacyClass privacyClass;
    /** The basis on which personal or restricted data is collected at all. */
    private @Nullable String legalBasis;
    /** Whether events of this contract may be used to train a model. */
    private boolean trainingAllowed;
    /** Volume consumers and capacity planning should expect. */
    private @Nullable Long expectedDailyVolume;
    /** How long accepted arrivals of this contract are kept. */
    private int retentionDays;
    /** How long quarantined arrivals are kept; never longer than the accepted stream. */
    private int quarantineRetentionDays;
    /** The one accountable business or domain owner. */
    private String businessOwner;
    /** The one technical steward who maintains it. */
    private String technicalSteward;
    /** Where this contract stands in its lifecycle. */
    private EventDefinitionStatus status;
    /** The contract version that replaces this one. */
    private @Nullable UUID replacementDefinitionId;
    /** When it was last produced, required once deprecated. */
    private @Nullable Instant lastProductionAt;
    /** What happens to the retained data, required once deprecated. */
    private @Nullable String deletionPlan;
    /** UTC instant reviewed. */
    private @Nullable Instant reviewedAt;
    /** UTC instant activated. */
    private @Nullable Instant activatedAt;
    /** UTC instant deprecated. */
    private @Nullable Instant deprecatedAt;
    /** UTC instant retired. */
    private @Nullable Instant retiredAt;
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
