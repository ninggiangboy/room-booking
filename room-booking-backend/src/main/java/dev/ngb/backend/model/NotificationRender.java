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
 * What the words came out as for one intent, channel and locale.
 *
 * <p>Append-only, and stored as hashes and a reference rather than as the rendered body, so the audit
 * trail does not become a second copy of everybody's mail. A blocked render names the variables that
 * were missing; a model never fills them in.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("notification_renders")
public class NotificationRender {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Intent being rendered. */
    private UUID notificationIntentId;
    /** Channel rendered for. */
    private NotificationChannel channel;
    /** Locale rendered in. */
    private String locale;
    /** Template version used; required for a successful render. */
    private @Nullable UUID notificationTemplateId;
    /** SHA-256 of the allowlisted facts fed into the template. */
    private String variableSourceHash;
    /** SHA-256 of what was produced. */
    private @Nullable String contentHash;
    /** Pointer to the protected rendered artifact. */
    private @Nullable String artifactReference;
    /** Whether the words could be produced. */
    private RenderState renderState;
    /** Why rendering failed. */
    private @Nullable String errorCode;
    /** Required variables that were absent, for a blocked render. */
    private String[] missingVariables;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this render produced sendable content.
     *
     * @return {@code true} only for a successful render
     */
    public boolean isRendered() {
        return renderState == RenderState.RENDERED;
    }
}
