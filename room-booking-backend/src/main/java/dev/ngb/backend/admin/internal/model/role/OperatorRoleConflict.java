package dev.ngb.backend.admin.internal.model.role;

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
 * A declared pair of operator roles that one person may not hold at once.
 * <p>Separation of duties written down and not enforced survives exactly until the week somebody is
 * on leave, so this is the row an assignment is checked against at insert. The pair is stored in
 * one
 * order, so a conflict is one row rather than two that can disagree about whether it still
 * stands.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("operator_role_conflicts")
public class OperatorRoleConflict {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The alphabetically first of the two role keys, so a conflict is one row rather than two. */
    private String lowerRoleKey;
    /** The alphabetically second of the two role keys. */
    private String higherRoleKey;
    /** Why the two roles may not be held together. */
    private RoleConflictBasis conflictBasis;
    /** What goes wrong when one person holds both, written for whoever asks for an exception. */
    private String rationale;
    /** Whether an exception to this conflict may ever be granted. */
    private boolean exceptionAllowed;
    /** Which approval role may grant that exception. */
    private @Nullable String exceptionApprovalRole;
    /** The account holder who declared the conflict. */
    private UUID declaredBy;
    /** UTC instant the conflict was declared. */
    private Instant declaredAt;
    /** UTC instant the conflict stopped applying. */
    private @Nullable Instant withdrawnAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String withdrawalReason;
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
