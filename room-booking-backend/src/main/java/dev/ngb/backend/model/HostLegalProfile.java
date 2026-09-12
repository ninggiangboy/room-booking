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
 * The legal identity of a selling host: who they are, where, and under what legal form.
 *
 * <p>This is not {@code host_profiles}, which holds a bio and a coarse identity status. A published
 * host receives money and provides regulated accommodation, so the platform has to know their legal
 * name, their jurisdiction, and — for a business — who ultimately owns it.</p>
 *
 * <p>The legal name is stored both in the clear and as {@link #legalNameDigest}, so screening can
 * match on the digest without scanning plaintext names across every host on the platform.</p>
 *
 * <p>Individual and business profiles carry structurally different evidence and the database refuses
 * the mixed row: an age check that silently passes on a company that has no age is worse than one
 * that fails outright.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_legal_profiles")
public class HostLegalProfile {

    /** Primary key of the profile. */
    @Id
    private @Nullable UUID id;
    /** Account holder this profile establishes the identity of. */
    private UUID accountHolderId;
    /** Whether the seller is a natural person or a registered business. */
    private HostProfileType profileType;
    /** Registered legal name of the seller. */
    private String legalName;
    /** SHA-256 digest of the legal name, indexed for screening without plaintext scans. */
    private String legalNameDigest;
    /** Date of birth; present for an individual, absent for a business. */
    private @Nullable LocalDate dateOfBirth;
    /** Incorporation date; present for a business, absent for an individual. */
    private @Nullable LocalDate incorporationDate;
    /** Company registration number; required for a business. */
    private @Nullable String registrationNumber;
    /** ISO 3166-1 alpha-2 country the seller is registered in. */
    private String registeredCountry;
    /** Reference to the protected registered-address record. */
    private @Nullable String addressReference;
    /** Market the seller operates in, whose rules decide their eligibility. */
    private String marketCode;
    /** How far the profile has progressed through verification. */
    private HostProfileLifecycle lifecycleState;
    /** UTC instant after which the profile's personal data must be erased. */
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

    /**
     * Reports whether the profile's evidence is currently good enough to decide eligibility from.
     *
     * <p>Being verified does not itself confer any capability: policy reads this alongside screening
     * and registration evidence and records its conclusion in {@code host_eligibility_decisions}.</p>
     *
     * @return {@code true} when verification has completed and has not gone stale
     */
    public boolean isVerified() {
        return lifecycleState == HostProfileLifecycle.VERIFIED;
    }
}
