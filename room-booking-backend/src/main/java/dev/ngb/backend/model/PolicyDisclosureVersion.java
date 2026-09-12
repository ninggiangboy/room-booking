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
 * The words a guest actually read, in one locale.
 *
 * <p>A settlement argument is rarely about the rule document -- it is about what the booking page said.
 * The semantic hash is over the meaning rather than the markup, so a styling change does not
 * invalidate an acceptance and a wording change does.</p>
 *
 * <p>Approved rows are frozen by trigger. New locales may still be added to a published policy,
 * because a translation approved in March is a legitimate addition to terms published in January; the
 * unique key on policy version and locale is what stops one quietly replacing another.</p>
 *
 * <p>The row carries no optimistic lock and no update timestamp, because it is never updated.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("policy_disclosure_versions")
public class PolicyDisclosureVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Terms this text discloses. */
    private UUID policyVersionId;
    /** BCP 47 locale the text is written in. */
    private String locale;
    /** Heading shown to the guest. */
    private String title;
    /** The disclosure text itself. */
    private String body;
    /** Machine-readable summary rendered beside the text. */
    private @Nullable JsonDocument structuredSummary;
    /** SHA-256 over the meaning, lowercase hex. Acceptances cite this. */
    private String semanticContentHash;
    /** Where the text came from. */
    private TranslationSource translationSource;
    /** Locale it was translated from, where it was. */
    private @Nullable String translatedFromLocale;
    /** Who approved it as terms. */
    private @Nullable UUID approvedByActorId;
    /** When they did. A machine translation may never carry this. */
    private @Nullable Instant approvedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this text may be presented as terms.
     *
     * @return {@code true} when it has been approved
     */
    public boolean isApproved() {
        return approvedAt != null;
    }
}
