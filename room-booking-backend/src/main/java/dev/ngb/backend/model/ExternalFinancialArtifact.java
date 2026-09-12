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
import org.springframework.data.relational.core.mapping.Table;

/**
 * The file or feed a reconciliation ran against, kept by reference and hash rather than re-fetched.
 *
 * <p>A settlement report downloaded again a month later may not be the report that was reconciled, and
 * an argument about the numbers has to be settled from what was actually read. The content hash is
 * unique, so a re-uploaded report cannot be reconciled a second time and double every row it
 * contains.</p>
 *
 * <p>The content is frozen once ingested. Only the parsing outcome -- whether it was parsed, how many
 * rows it held, and how complete it is believed to be -- may still be written.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("external_financial_artifacts")
public class ExternalFinancialArtifact {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Where the evidence came from. */
    private ExternalArtifactSource sourceKind;
    /** Merchant account it covers, for provider sources. */
    private @Nullable UUID providerAccountId;
    /** Entity whose position it bears on. */
    private UUID legalEntityId;
    /** Opaque reference to the bank account, for bank sources. */
    private @Nullable String bankAccountReference;
    /** ISO 4217 code, when the artifact is single-currency. */
    private @Nullable String currency;
    /** UTC instant the covered period begins. */
    private Instant coverageStart;
    /** UTC instant it ends. */
    private Instant coverageEnd;
    /** IANA zone the source cut its period in. */
    private String cutoffTimezone;
    /** Where the raw bytes are retained. */
    private String artifactReference;
    /** SHA-256 of those bytes, lowercase hex; unique across all artifacts. */
    private String contentHash;
    /** Schema the source produced. */
    private String schemaVersion;
    /** Parser that read it. */
    private String parserVersion;
    /** Sequence the source assigned, where it numbers its reports. */
    private @Nullable Integer sequenceNumber;
    /** Whether the whole period is believed present. */
    private ArtifactCompleteness completeness;
    /** How long the raw artifact must be kept. */
    private RetentionClass retentionClass;
    /** Earlier artifact this one replaces. */
    private @Nullable UUID supersedesArtifactId;
    /** UTC instant the source produced it. */
    private @Nullable Instant generatedAt;
    /** UTC instant the platform received it. */
    private Instant receivedAt;
    /** UTC instant it was successfully read. */
    private @Nullable Instant parsedAt;
    /** Why reading failed, when it did. */
    private @Nullable String parseFailureCode;
    /** Rows extracted from it. */
    private int rowCount;

    /** UTC instant the row was written. */
    @CreatedDate
    private Instant createdAt;
}
