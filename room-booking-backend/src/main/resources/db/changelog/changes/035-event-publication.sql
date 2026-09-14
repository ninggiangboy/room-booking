--liquibase formatted sql

-- Spring Modulith's event publication registry gives @ApplicationModuleListener an at-least-once
-- delivery guarantee: every (event, listener) pair is written to this table in the same transaction
-- as the event's publisher, and the row is marked complete only once the listener has actually run.
-- A process that dies between commit and listener leaves the row outstanding, and
-- spring.modulith.events.republish-outstanding-events-on-restart replays it on the next boot instead
-- of losing it silently -- the exact failure mode a bare ApplicationEventPublisher has today.
--
-- This table is owned by spring-modulith-events-jdbc, not by this application, and its shape is
-- deliberately NOT brought into line with this repository's schema conventions
-- (docs/conventions/03-entities-and-persistence.md): no VARCHAR(n) + CHECK in place of TEXT, no
-- optimistic-locking version column, no retain_until. The framework's own queries depend on exactly
-- this shape, so it is reproduced verbatim from Spring Modulith's documented PostgreSQL schema
-- (2.1.1) rather than "corrected." See docs/data-model/035-event-publication.md for the full
-- rationale and docs/architecture/event-publication-registry.md for why this table is not a
-- replacement for outbox_events (migration 012).
--
-- spring.modulith.events.jdbc.schema-initialization.enabled is set to false in
-- application.properties precisely so this migration, and not the framework at startup, is the only
-- thing that ever creates this table.

--changeset ninggiangboy:035-01-event-publication
CREATE TABLE event_publication (
    id                     UUID NOT NULL,
    listener_id            TEXT NOT NULL,
    event_type             TEXT NOT NULL,
    serialized_event       TEXT NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);
CREATE INDEX event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
--rollback DROP TABLE event_publication;
