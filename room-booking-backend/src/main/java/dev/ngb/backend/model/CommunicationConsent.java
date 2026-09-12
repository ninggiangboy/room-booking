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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Evidence that somebody agreed to be contacted, or withdrew that agreement.
 *
 * <p>Withdrawal is a timestamp on the same row rather than a delete, because the question this table
 * answers is "prove you had permission when you sent it". At most one live consent exists per actor,
 * category, channel and market.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("communication_consents")
public class CommunicationConsent {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Whose consent this is. */
    private UUID accountHolderId;
    /** Category consented to. */
    private String categoryCode;
    /** Channel consented to. */
    private NotificationChannel channel;
    /** Market whose rules the consent was collected under. */
    private @Nullable String marketCode;
    /** Lawful basis the communication rests on. */
    private ConsentLegalBasis legalBasis;
    /** Notice text shown when consent was collected. */
    private String noticeReference;
    /** Version of that notice. */
    private Integer noticeVersion;
    /** Where the consent was collected. */
    private ConsentGrantSource grantSource;
    /** Evidence of the grant. */
    private @Nullable String grantEvidenceReference;
    /** When consent was given. */
    private Instant grantedAt;
    /** When it was withdrawn; null while live. */
    private @Nullable Instant withdrawnAt;
    /** How it was withdrawn. */
    private @Nullable ConsentWithdrawalSource withdrawalSource;
    /** Evidence of the withdrawal. */
    private @Nullable String withdrawalEvidenceRef;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether this consent still permits sending.
     *
     * @return {@code true} while it has not been withdrawn
     */
    public boolean isLive() {
        return withdrawnAt == null;
    }
}
