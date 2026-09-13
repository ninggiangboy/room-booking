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
 * One consumer that declares itself a reader of one event contract.
 *
 * <p>The list exists so retirement can be refused while somebody is still reading, and so the
 * policy for an unknown field is stated by the consumer in advance rather than discovered when the
 * producer adds one.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("event_definition_consumers")
public class EventDefinitionConsumer {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The event definition this row belongs to. */
    private UUID eventDefinitionId;
    /** The component that declares itself a reader. */
    private String consumerName;
    /** Which consumer kind this row carries. */
    private EventConsumerKind consumerKind;
    /** Which version of its own contract the consumer reads under. */
    private String contractVersion;
    /** What this consumer does with a field it does not recognise. */
    private UnknownFieldPolicy unknownFieldPolicy;
    /** UTC instant the consumer declared itself. */
    private Instant declaredAt;
    /** UTC instant it stopped reading; a standing consumer blocks retirement. */
    private @Nullable Instant withdrawnAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
