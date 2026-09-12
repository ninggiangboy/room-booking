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
 * One item in the manifest of what was sent to defend a dispute.
 *
 * <p>Mutable while it is being assembled and frozen the moment it is sent: a representment the
 * platform cannot reproduce byte for byte is not a defence it can stand behind, and "we changed
 * the file after submitting" is the sentence that loses the case. A row trigger enforces the
 * freeze.</p>
 *
 * <p>A file nobody scanned, or one that came back infected, is never submitted.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_dispute_evidence")
public class PaymentDisputeEvidence {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Case this evidence defends. */
    private UUID disputeId;
    /** What kind of proof it is. */
    private String evidenceType;
    /** Name shown to the agent assembling the case. */
    private String displayName;
    /** Hex SHA-256 of the content, so the submitted bytes are identifiable. */
    private String contentDigest;
    /** Pointer to the stored content. */
    private @Nullable String storageReference;
    /** Media type of the content. */
    private @Nullable String contentType;
    /** Size of the content in bytes. */
    private @Nullable Long byteSize;
    /** Malware-scan result. */
    private EvidenceScanState scanState;
    /** Whether the content was minimised before submission. */
    private boolean redactionApplied;
    /** Kind of actor that assembled it. */
    private BookingActorType preparedByActorType;
    /** Identity of that actor, when it has one. */
    private @Nullable UUID preparedByActorId;
    /** Actor that authorised sending it. */
    private @Nullable UUID approvedByActorId;
    /** UTC instant it was authorised. */
    private @Nullable Instant approvedAt;
    /** UTC instant it was sent; the row is frozen from then on. */
    private @Nullable Instant submittedAt;
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
     * Whether this item may still be edited.
     *
     * @return {@code true} until it has been submitted to the provider
     */
    public boolean isEditable() {
        return submittedAt == null;
    }

    /**
     * Whether this item is safe to send.
     *
     * @return {@code true} when it is authorised and carries no dangerous content
     */
    public boolean isSubmittable() {
        return approvedAt != null
                && (scanState == EvidenceScanState.CLEAN
                        || scanState == EvidenceScanState.NOT_APPLICABLE);
    }
}
