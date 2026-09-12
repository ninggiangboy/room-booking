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
 * A local authorisation to let accommodation, and the limits that come with it.
 *
 * <p>Short-term rental is regulated locally rather than by the platform, so these are facts about a
 * jurisdiction. {@link #annualNightLimit} is the one that reaches into the product: where a
 * jurisdiction caps how many nights a property may be let per year, the calendar has to enforce it.
 * A limit of zero would be a refusal rather than a limit, and the database refuses it.</p>
 *
 * <p>{@link RegulatoryRegistrationStatus#NOT_REQUIRED} is recorded explicitly rather than left as a
 * missing row, so "this jurisdiction requires no permit" is distinguishable from "nobody has checked
 * yet".</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("regulatory_registrations")
public class RegulatoryRegistration {

    /** Primary key of the registration record. */
    @Id
    private @Nullable UUID id;
    /** Legal profile the authorisation belongs to. */
    private UUID hostLegalProfileId;
    /** Market whose rules require it. */
    private String marketCode;
    /** Kind of authorisation. */
    private RegulatoryRegistrationType registrationType;
    /** Authority that issued it. */
    private @Nullable String authorityName;
    /** Reference number the authority assigned. */
    private @Nullable String registrationNumber;
    /** Identifier of the sub-market jurisdiction the rule comes from. */
    private @Nullable String jurisdictionReference;
    /** Maximum nights per year the jurisdiction permits; enforced by the calendar. */
    private @Nullable Integer annualNightLimit;
    /** How far the claim has been checked. */
    private RegulatoryRegistrationStatus status;
    /** Reference to supporting evidence in protected storage. */
    private @Nullable String evidenceReference;
    /** Civil date the authorisation takes effect. */
    private @Nullable LocalDate validFrom;
    /** Civil date it lapses. */
    private @Nullable LocalDate validUntil;
    /** UTC instant the claim was confirmed with the authority. */
    private @Nullable Instant verifiedAt;
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
