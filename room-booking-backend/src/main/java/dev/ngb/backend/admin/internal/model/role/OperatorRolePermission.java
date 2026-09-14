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
import org.springframework.data.relational.core.mapping.Table;
import dev.ngb.backend.platform.DataPrivacyClass;

import dev.ngb.backend.platform.DataPrivacyClass;


/**
 * One permission a role version confers.
 * <p>The permissions are the role. Membership is frozen when the role leaves draft, and a role
 * cannot
 * be activated with nothing in it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("operator_role_permissions")
public class OperatorRolePermission {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The role version this permission belongs to. */
    private UUID roleDefinitionId;
    /** Stable key naming the permission, as the authorization layer knows it. */
    private String permissionKey;
    /** Whether the permission reads, changes state, issues a command, exports or administers. */
    private OperatorPermissionKind permissionKind;
    /** The domain that owns whatever this permission reaches. */
    private String owningDomain;
    /** The resource scope the permission is exercised at. */
    private PermissionScopeType scopeType;
    /** The most sensitive class of data this permission reaches. */
    private DataPrivacyClass dataSensitivity;
    /** Whether using this permission needs a second person at the point of use. */
    private boolean requiresSecondApproval;
    /** Whether every use of this permission has to carry an approved reason code. */
    private boolean requiresReasonCode;
    /** Why this role needs this permission, recorded when the role version was written. */
    private String justification;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
