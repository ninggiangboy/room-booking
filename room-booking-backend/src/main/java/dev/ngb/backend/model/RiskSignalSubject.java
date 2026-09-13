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
 * One subject an observation is about.
 *
 * <p>A single login observation can concern an account, a device and an instrument at once. Keeping
 * that as rows rather than three nullable columns is what lets one subject's evidence be gathered
 * without scanning every signal in the system.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_signal_subjects")
public class RiskSignalSubject {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The observation. */
    private UUID riskSignalId;
    /** The subject it concerns. */
    private UUID riskSubjectId;
    /** How that subject relates to the observation. */
    private SignalSubjectRole subjectRole;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
