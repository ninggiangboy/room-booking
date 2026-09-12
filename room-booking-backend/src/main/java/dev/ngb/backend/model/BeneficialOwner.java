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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A person who ultimately owns or controls a business host.
 *
 * <p>Screening a company tells you nothing if the people behind it are the ones on a sanctions list,
 * which is why owners are declared and screened individually.</p>
 *
 * <p>Ownership is held in basis points rather than as a percentage: 25% is {@code 2500} exactly, so a
 * threshold deciding whether someone must be screened at all has no floating-point rounding to argue
 * about. A declared owner qualifies through ownership, control, or both — neither would leave the row
 * unable to explain why the person was declared.</p>
 *
 * <p>Registers change. Superseding a row rather than editing it keeps the record of who was declared
 * when a past decision was made.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("beneficial_owners")
public class BeneficialOwner {

    /** Primary key of the declaration. */
    @Id
    private @Nullable UUID id;
    /** Business profile this person owns or controls. */
    private UUID hostLegalProfileId;
    /** Full legal name of the person. */
    private String fullName;
    /** SHA-256 digest of the name, indexed for screening without plaintext scans. */
    private String fullNameDigest;
    /** Date of birth, needed to distinguish people who share a name. */
    private LocalDate dateOfBirth;
    /** ISO 3166-1 alpha-2 nationality. */
    private @Nullable String nationality;
    /** Ownership share in basis points; {@code 2500} is 25%. */
    private @Nullable Integer ownershipBasisPoints;
    /** Whether the person exercises control regardless of any ownership share. */
    private boolean isControlPerson;
    /** Reference to the protected address record. */
    private @Nullable String addressReference;
    /** UTC instant the declaration was made. */
    private Instant declaredAt;
    /** Declaration that replaced this one. */
    private @Nullable UUID supersededBy;
    /** UTC instant after which this person's data must be erased. */
    private @Nullable Instant retainUntil;
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
