package dev.ngb.backend.support.internal.model.claim;

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
 * One claimed loss, valued by a named method.
 *
 * <p>Estimates, invoices and receipts prove different things, so the document kind is recorded beside the
 * amount it supports. Accepted and rejected amounts are separate with an adjustment reason, because
 * giving somebody less has to be explainable per line.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("damage_claim_items")
public class DamageClaimItem {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The damage claim this row belongs to. */
    private UUID damageClaimId;
    /** Position of this row within its parent, unique there. */
    private short lineNumber;
    /** Category. */
    private ClaimItemCategory category;
    /** Reference to the description, held in its owning system rather than copied here. */
    private String descriptionReference;
    /** Ownership relation. */
    private ItemOwnershipRelation ownershipRelation;
    /** Condition before the stay, which bears on what may be recovered. */
    private ItemConditionBefore conditionBefore;
    /** Age of the item in months, where the claimant can say. */
    private @Nullable Integer ageMonths;
    /** Which alleged loss kind this row carries. */
    private AllegedLossKind allegedLossKind;
    /** ISO 4217 alphabetic code the amounts on this row are denominated in. */
    private String currency;
    /** Requested amount, in integer minor units of its currency. */
    private long requestedAmountMinor;
    /** Original cost, in integer minor units of its currency. */
    private @Nullable Long originalCostMinor;
    /** Repair estimate, in integer minor units of its currency. */
    private @Nullable Long repairEstimateMinor;
    /** Replacement cost, in integer minor units of its currency. */
    private @Nullable Long replacementCostMinor;
    /** Residual value of the damaged item, in minor units. */
    private long salvageValueMinor;
    /** Valuation method. */
    private @Nullable ValuationMethod valuationMethod;
    /** Which version of the valuation method applies. */
    private @Nullable Integer valuationMethodVersion;
    /** Depreciation rule. */
    private @Nullable String depreciationRule;
    /** Depreciation deducted, in minor units; a non-zero value needs a named rule. */
    private long depreciationAmountMinor;
    /** This line’s share of the deductible, in minor units. */
    private long deductibleShareMinor;
    /** The category ceiling this line was measured against. */
    private @Nullable Long categoryCapMinor;
    /** What was accepted; with the rejected half it must equal what was requested. */
    private @Nullable Long acceptedAmountMinor;
    /** What was refused, which above zero needs an adjustment reason. */
    private @Nullable Long rejectedAmountMinor;
    /** Why the accepted amount is lower than the request. */
    private @Nullable String adjustmentReason;
    /** What remains uncertain about this line, so the result can be explained. */
    private @Nullable String uncertaintyNoteReference;
    /** What kind of document supports it; an estimate is not proof of a paid cost. */
    private @Nullable SupportingDocumentKind supportingDocumentKind;
    /** The evidence supporting this line. */
    private UUID[] supportingEvidenceIds;
    /** The case finding this row belongs to. */
    private @Nullable UUID caseFindingId;
    /** Any earlier recovery on the same loss, so it cannot be recovered twice. */
    private @Nullable String priorRecoveryReference;
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
