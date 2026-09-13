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
 * One term of an offer, shaped like the remedy line it may later become.
 *
 * <p>Carries a remedy code, a beneficiary, a funder and an amount, so an accepted offer can be checked
 * against the same rules a decision would face.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_offer_lines")
public class CaseOfferLine {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The case offer this row belongs to. */
    private UUID caseOfferId;
    /** Position of this row within its parent, unique there. */
    private short lineNumber;
    /** Remedy code. */
    private String remedyCode;
    /** The remedy catalog version this row belongs to. */
    private @Nullable UUID remedyCatalogVersionId;
    /** Which beneficiary kind this row carries. */
    private BeneficiaryKind beneficiaryKind;
    /** The beneficiary account holder this row belongs to. */
    private @Nullable UUID beneficiaryAccountHolderId;
    /** Which funder kind this row carries. */
    private FunderKind funderKind;
    /** The funder account holder this row belongs to. */
    private @Nullable UUID funderAccountHolderId;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private String currency;
    /** Amount, in integer minor units of its currency. */
    private long amountMinor;
    /** Reference to the source, held in its owning system rather than copied here. */
    private @Nullable String sourceReference;
    /** Reference to the non financial term, held in its owning system rather than copied here. */
    private @Nullable String nonFinancialTermReference;
    /** Stable key naming the explanation template. */
    private @Nullable String explanationTemplateKey;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
