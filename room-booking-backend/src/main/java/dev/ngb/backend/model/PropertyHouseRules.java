package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalTime;
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
 * The rules a guest accepts when booking a property, and the times a stay runs between.
 *
 * <p>Every time here is a <em>civil</em> time in the property's own zone: 15:00 means three in the
 * afternoon there, which is why {@code properties.time_zone} is load-bearing rather than decorative.
 * Storing these as instants would be meaningless, since they recur daily and shift with DST.</p>
 *
 * <p>The primary key is the property, so a property has exactly one set of rules. Quiet hours may
 * legitimately cross midnight, so no ordering is imposed between them.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("property_house_rules")
public class PropertyHouseRules {

    /** Property these rules apply to; also the primary key. */
    @Id
    private UUID propertyId;
    /** Earliest civil time a guest may check in, in the property's zone. */
    private LocalTime checkInFrom;
    /** Latest civil time a guest may check in without arranging it. */
    private @Nullable LocalTime checkInUntil;
    /** Civil time a guest must have left by. */
    private LocalTime checkOutBy;
    /** Civil time quiet hours begin; may be later in the day than they end. */
    private @Nullable LocalTime quietHoursFrom;
    /** Civil time quiet hours end. */
    private @Nullable LocalTime quietHoursUntil;
    /** Minimum age of the booking guest, where the property sets one. */
    private @Nullable Short minimumGuestAge;
    /** Whether pets are permitted. */
    private RulePolicy petsPolicy;
    /** Where smoking is permitted, if anywhere. */
    private SmokingPolicy smokingPolicy;
    /** Whether parties or events are permitted. */
    private RulePolicy partiesPolicy;
    /** Whether children are permitted. */
    private RulePolicy childrenPolicy;
    /** Any further rules the host states in their own words. */
    private @Nullable String additionalRules;
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
