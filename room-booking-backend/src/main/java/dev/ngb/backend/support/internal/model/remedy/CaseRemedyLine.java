package dev.ngb.backend.support.internal.model.remedy;

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
 * One beneficiary, one funder, one amount.
 *
 * <p>Host-funded, platform-funded, insurer-funded and guest-funded values never substitute for one
 * another, and a host cannot be made to fund what a host does not control.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_remedy_lines")
public class CaseRemedyLine {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The case remedy this row belongs to. */
    private UUID caseRemedyId;
    /** Position within the remedy, unique there. */
    private short lineNumber;
    /** Who receives the value. */
    private BeneficiaryKind beneficiaryKind;
    /** The receiving account, when the beneficiary is a guest or a host. */
    private @Nullable UUID beneficiaryAccountHolderId;
    /** Whose money it is. */
    private FunderKind funderKind;
    /** The funding account, when the funder is a guest or a host. */
    private @Nullable UUID funderAccountHolderId;
    /** ISO 4217 alphabetic code this line is denominated in; the remedy total is in the same currency. */
    private String currency;
    /** What this line moves, in integer minor units. The lines must sum to the remedy total. */
    private long amountMinor;
    /** What the money comes out of, which is what decides whether a host may fund it. */
    private RemedySourceKind sourceKind;
    /** The owning domain’s identifier for that source, opaque to this domain. */
    private @Nullable String sourceReference;
    /** The source line in its owning domain, where that domain exposes one. */
    private @Nullable UUID sourceLineId;
    /** The claimed item this line pays for, when it pays for one. */
    private @Nullable UUID damageClaimItemId;
    /** Whether this line adjusts the historical price, and what document it needs. */
    private RemedyTaxTreatment taxTreatment;
    /** The tax document behind a price-adjusting line. */
    private @Nullable String taxDocumentReference;
    /** The scope the ceiling behind this line was measured over. */
    private RemedyLineCeilingScope ceilingScope;
    /** The hold taken on a source ceiling, when the line is guarded that way. */
    private @Nullable UUID remedyReservationId;
    /** The budget window consumed, when the line is guarded that way instead. */
    private @Nullable UUID remedyBudgetWindowId;
    /** Approved reason code; free text never stands in for one. */
    private String reasonCode;
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
