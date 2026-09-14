package dev.ngb.backend.stay.internal.model.instruction;

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
import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.AssuranceLevel;
import dev.ngb.backend.platform.AuditOutcome;

import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.AssuranceLevel;
import dev.ngb.backend.platform.AuditOutcome;


/**
 * One retrieval of one instruction band, and what came of it.
 *
 * <p>Append-only. This is the record consulted when a code turns out to have been used by somebody
 * who should not have had it, and its value depends entirely on nobody being able to tidy it
 * afterwards -- which is why it carries no version or modification instant.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("instruction_access_audit")
public class InstructionAccessAudit {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Version that was asked for. */
    private UUID instructionSetId;
    /** Stay it belongs to. */
    private UUID operationalStayId;
    /** Kind of actor that asked. */
    private ActorType actorType;
    /** Which account holder asked, where there was one. */
    private @Nullable UUID actorAccountHolderId;
    /** Session the request arrived on. */
    private @Nullable UUID authSessionId;
    /** Authentication strength at the time. */
    private @Nullable AssuranceLevel assuranceLevel;
    /** Opaque reference to the device or session risk assessment. */
    private @Nullable String deviceRiskReference;
    /** Why the caller says it needed the fields. */
    private String purposeCode;
    /** Band that was asked for. */
    private InstructionFieldClass requestedFieldClass;
    /** Bands actually released. */
    private String[] releasedFieldClasses;
    /** Whether it was allowed, denied, partial or failed. */
    private AuditOutcome decision;
    /** Why it was refused or only partly served. */
    private @Nullable String denialReason;
    /** Correlation identifier for the request. */
    private @Nullable UUID correlationId;
    /** When the retrieval happened. */
    private Instant occurredAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this retrieval exposed the secret band.
     *
     * <p>The first query a compromised-access investigation runs.</p>
     *
     * @return true when the secret band was released
     */
    public boolean exposedSecret() {
        for (String released : releasedFieldClasses) {
            if (InstructionFieldClass.SECRET.name().equals(released)) {
                return true;
            }
        }
        return false;
    }
}
