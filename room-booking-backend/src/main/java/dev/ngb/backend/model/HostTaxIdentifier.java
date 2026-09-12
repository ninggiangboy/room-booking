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
 * A tax identifier a host has declared for one market, and how far it has been checked.
 *
 * <p>The identifier itself is never stored in this row. {@link #identifierDigest} proves sameness,
 * {@link #identifierLast4} lets a host recognize which one they gave us, and the value lives behind
 * the secret boundary — a tax code is a durable government identifier, and a database dump should not
 * disclose one.</p>
 *
 * <p>{@link TaxRegistrationStatus#DECLARED} means the host typed it and nothing has confirmed it.
 * Treating a declaration as a validation is how withholding gets applied at the wrong rate for a year
 * before anyone notices.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_tax_identifiers")
public class HostTaxIdentifier {

    /** Primary key of the identifier record. */
    @Id
    private @Nullable UUID id;
    /** Legal profile the identifier belongs to. */
    private UUID hostLegalProfileId;
    /** Market the identifier is meaningful in. */
    private String marketCode;
    /** Kind of identifier, which decides validation, withholding, and invoice content. */
    private TaxIdentifierType identifierType;
    /** SHA-256 digest of the identifier; the value itself is not stored here. */
    private String identifierDigest;
    /** Last characters of the identifier, so a host can recognize which one this is. */
    private @Nullable String identifierLast4;
    /** Secret-manager reference holding the full value. */
    private String secretReference;
    /** How far the identifier has been checked. */
    private TaxRegistrationStatus registrationStatus;
    /** Whether withholding applies to payouts under this identifier. */
    private boolean withholdingApplies;
    /** Scope of seller reporting this identifier brings the host into. */
    private @Nullable String sellerReportingScope;
    /** UTC instant the identifier was confirmed with the issuing authority. */
    private @Nullable Instant validatedAt;
    /** UTC instant the identifier takes effect, inclusive. */
    private Instant effectiveFrom;
    /** UTC instant it stops applying, exclusive; {@code null} while current. */
    private @Nullable Instant effectiveUntil;
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
