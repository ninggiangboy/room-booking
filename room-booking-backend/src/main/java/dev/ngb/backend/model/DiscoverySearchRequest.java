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
 * The server-issued identity of one search, and the versions that served it.
 *
 * <p>Every exposure and every ranked event hangs off this row, which is what makes a fabricated
 * impression detectable: it would have to name a search that was never issued, at a position that
 * was never returned. Each stage of the funnel can only narrow the candidate set.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("discovery_search_requests")
public class DiscoverySearchRequest {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Server-issued identity of this search, which clients echo back on every event. */
    private String searchRequestKey;
    /** The ranking epoch this row belongs to. */
    private UUID rankingEpochId;
    /** The ranking policy version this row belongs to. */
    private UUID rankingPolicyVersionId;
    /** The ranking model version this row belongs to. */
    private @Nullable UUID rankingModelVersionId;
    /** The account holder this row belongs to. */
    private @Nullable UUID accountHolderId;
    /** Analytical pseudonym for the session that issued the search. */
    private String sessionPseudonym;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** BCP 47 locale the content is written in. */
    private String locale;
    /** ISO 4217 alphabetic code the guest asked prices in. */
    private String requestedCurrency;
    /** The ordering the guest asked for. */
    private DiscoverySortMode sortMode;
    /** Whether the results were personalized, and if not, why not. */
    private PersonalizationState personalizationState;
    /** Digest of the normalized query, so a cursor can prove it belongs to this search. */
    private String normalizedQueryDigest;
    /** The destination geo area this row belongs to. */
    private @Nullable UUID destinationGeoAreaId;
    /** How the candidate set was framed. */
    private DiscoverySearchMode searchMode;
    /** Civil arrival date of the stay searched for. */
    private @Nullable LocalDate checkInDate;
    /** Civil departure date; the range is half-open. */
    private @Nullable LocalDate checkOutDate;
    /** Nights in the searched stay, which must equal the range it spans. */
    private @Nullable Short nightCount;
    /** Party size the search was run for. */
    private @Nullable Short guestCount;
    /** Days between the search and the arrival date. */
    private @Nullable Short leadTimeDays;
    /** Candidates after geographic retrieval, before any other filter. */
    private int geoCandidateCount;
    /** Candidates remaining after hard eligibility constraints. */
    private int eligibleCandidateCount;
    /** Candidates for which an authoritative trip total was obtained. */
    private int pricedCandidateCount;
    /** Candidates actually scored and ordered. */
    private int rankedCandidateCount;
    /** Results returned on this page, which can never exceed what was ranked. */
    private int returnedCount;
    /** One-based page this request served. */
    private short pageNumber;
    /** How far up the geographic hierarchy priors had to be taken from. */
    private PriorFallbackLevel priorFallbackLevel;
    /** Why something other than the intended ranker served this search. */
    private DiscoveryFallbackCode fallbackCode;
    /** Milliseconds the whole search took. */
    private @Nullable Integer totalLatencyMs;
    /** Milliseconds spent on optional personalization enrichment, which degrades independently. */
    private @Nullable Integer enrichmentLatencyMs;
    /** Correlation identifier tying this search to its request logs. */
    private @Nullable String correlationId;
    /** UTC instant the search was served. */
    private Instant occurredAt;
    /** UTC instant after which this search log must be deleted. */
    private Instant expiresAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
