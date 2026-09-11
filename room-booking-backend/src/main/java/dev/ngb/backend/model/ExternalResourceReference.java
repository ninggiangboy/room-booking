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
 * Resolution between a resource held at an external provider and the aggregate it represents here.
 *
 * <p>Both directions of the mapping are constrained in the database, and both are needed. Without
 * provider-to-platform uniqueness an inbound webhook is ambiguous; without platform-to-provider
 * uniqueness a retried submission can create a second provider resource that nothing reconciles.</p>
 *
 * <p>Use this shared registry only where several domains genuinely need the resolution. An
 * integration owned by one domain keeps the provider tuple in its own table, where the domain's
 * lifecycle and retention rules apply.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("external_resource_references")
public class ExternalResourceReference {

    /** Primary key of the reference. */
    @Id
    private @Nullable UUID id;
    /** Provider account the external identifier is meaningful within. */
    private String providerAccountKey;
    /** Version of that account's configuration, so a credential rotation is visible. */
    private short providerAccountVersion;
    /** Kind of resource held at the provider, such as {@code PAYMENT_INTENT}. */
    private String resourceType;
    /** Provider-assigned identifier; never a provider secret. */
    private String externalId;
    /** Kind of platform aggregate the resource represents. */
    private String internalAggregateType;
    /** Identifier of that aggregate. */
    private UUID internalAggregateId;
    /** Whether this is the live link, or a retained historical one. */
    private ExternalReferenceLifecycle lifecycleState;
    /** UTC instant the link was established. */
    private Instant linkedAt;
    /** UTC instant the link stopped being live; {@code null} while active. */
    private @Nullable Instant detachedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;
}
