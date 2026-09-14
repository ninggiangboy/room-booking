package dev.ngb.backend.analytics.internal.model.contract;

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
 * A declared edge from one dataset version to one it reads.
 *
 * <p>Lineage is a set of rows rather than a field, because the classification rule has to be
 * checkable: a downstream product may not be less restrictive than its upstream unless somebody
 * recorded an approved transformation on this edge.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("data_product_dependencies")
public class DataProductDependency {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The downstream dataset version that reads. */
    private UUID dataProductId;
    /** The dataset version it reads from. */
    private UUID upstreamDataProductId;
    /** How far behind the upstream watermark a run may still read. */
    private int minimumWatermarkLagMinutes;
    /** Whether a run may proceed without this input at all. */
    private boolean required;
    /** Who approved a looser classification downstream; absent means none was. */
    private @Nullable String declassificationApproval;
    /** Why the transformation genuinely removes the exposure. */
    private @Nullable String declassificationReason;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
