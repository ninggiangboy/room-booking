package dev.ngb.backend.hostops.internal.model.advice;

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
import dev.ngb.backend.platform.JsonDocument;
import dev.ngb.backend.pricing.types.RecommendationConfidence;

/**
 * What a host was actually shown about one piece of advice: its evidence, its uncertainty, its
 * expected impact and its economic effect.
 * <p>Those four are the domain document's hard rule and four columns that cannot be null. A
 * recommendation that cannot fill all four is one the platform is not yet entitled to make, and the
 * place to discover that is the insert rather than the support case. The row also records that a
 * refusal and an override were on the screen, so "the host could have said no" is a stored fact
 * rather than an assumption about a release that shipped eighteen months ago.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_advice_disclosures")
public class HostAdviceDisclosure {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Which kind of advice was shown. */
    private HostAdviceKind adviceKind;
    /** The price recommendation that was shown. */
    private @Nullable UUID priceRecommendationId;
    /** The demand forecast that was shown. */
    private @Nullable UUID demandForecastId;
    /** The promotion suggestion that was shown. */
    private @Nullable UUID promotionSuggestionId;
    /** The outstanding checklist item that was shown. */
    private @Nullable UUID checklistStateId;
    /** The host the advice was shown to. */
    private UUID recipientAccountHolderId;
    /** Where the advice was shown. */
    private HostAdviceSurface surface;
    /** BCP 47 locale the advice was written in. */
    private String locale;
    /** What the advice rests on, in the words the host was shown. */
    private String evidenceSummary;
    /** Pointers to the rows the evidence was drawn from. */
    private @Nullable JsonDocument evidenceReferences;
    /** How sure the platform is, stated to the host rather than implied by a rounded number. */
    private String uncertaintyStatement;
    /** What the host should expect to happen if they take the advice. */
    private String expectedImpactStatement;
    /** What it costs and who bears it, in the words the host was shown. */
    private String economicEffectStatement;
    /** Who bears the cost of following the advice. */
    private AdviceCostBearer whoPays;
    /** How much weight the platform puts on the advice. */
    private RecommendationConfidence confidence;
    /** Whether the host was actually offered a way to decline. */
    private boolean optOutOffered;
    /** Whether the host was actually offered a way to change it. */
    private boolean overrideOffered;
    /** The model version behind the advice, where one produced it. */
    private @Nullable UUID modelVersionId;
    /** Digest of what was rendered, so what the host saw can be shown later to be unchanged. */
    private String renderedDigest;
    /** UTC instant the advice was shown. */
    private Instant disclosedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
