package dev.ngb.backend.model;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC entity representing one row in the {@code user_roles} join table.
 *
 * <p>Lombok generates accessors, constructors, and a builder. The composite {@link UserRoleId}
 * marked with {@code @Id} ensures a user cannot receive the same role twice.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("user_roles")
public class UserRole {

    /** Composite user-and-role primary key. */
    @Id
    private UserRoleId id;
    /** UTC instant at which the role was granted, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
