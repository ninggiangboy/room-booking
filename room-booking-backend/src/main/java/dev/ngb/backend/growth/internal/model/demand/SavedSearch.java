package dev.ngb.backend.growth.internal.model.demand;

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
import dev.ngb.backend.platform.JsonDocument;
import dev.ngb.backend.platform.StayRange;

import dev.ngb.backend.platform.JsonDocument;
import dev.ngb.backend.platform.StayRange;


/**
 * One search a guest kept, and how often they agreed to hear about it.
 *
 * <p>A search that notifies is a standing subscription: it names the consent permitting it and
 * the date it stops asking, because a subscription created once and never re-confirmed becomes a
 * channel the guest cannot remember agreeing to.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("saved_searches")
public class SavedSearch {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The guest whose search this is. */
    private UUID accountHolderId;
    /** What the guest called the search. */
    private String title;
    /** ISO 3166-1 alpha-2 market the search runs in. */
    private String marketCode;
    /** The area searched, where the search names one. */
    private @Nullable UUID geoAreaId;
    /** The search exactly as the guest built it. */
    private JsonDocument searchCriteria;
    /** Digest of the criteria, so the same search is not saved twice. */
    private String criteriaDigest;
    /** Half-open stay range the search covers. */
    private @Nullable StayRange stayRange;
    /** How many adults the search is for. */
    private @Nullable Short adultCount;
    /** How many children the search is for. */
    private @Nullable Short childCount;
    /** How often the guest agreed to hear about matches. */
    private SavedSearchCadence notifyCadence;
    /** The consent that permits those messages. */
    private @Nullable UUID communicationConsentId;
    /** UTC instant the search was last run. */
    private @Nullable Instant lastEvaluatedAt;
    /** How many matches that run found. */
    private @Nullable Integer lastResultCount;
    /** UTC instant the guest was last told about matches. */
    private @Nullable Instant lastNotifiedAt;
    /** Where the saved search stands. */
    private SavedSearchState state;
    /** UTC instant the subscription stops, so it is never open-ended. */
    private @Nullable Instant expiresAt;
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
