--liquibase formatted sql

-- Migration 037-04-rewrite-capability-grants renamed capability_grants.grantee_type's USER value to
-- PERSON once PrincipalType (the shared Java enum both capability_grants and capability_restrictions
-- read) dropped USER. capability_restrictions was never in that migration's "repoint identity
-- satellites" list, so its check constraint still allows only USER -- unreachable, since
-- PrincipalType has no such value. This never surfaced because nothing issued a restriction yet.

--changeset ninggiangboy:038-01-capability-restrictions-principal-type
ALTER TABLE capability_restrictions DROP CONSTRAINT ck_capability_restrictions_principal;
ALTER TABLE capability_restrictions
    ADD CONSTRAINT ck_capability_restrictions_principal
        CHECK (principal_type IN ('PERSON', 'ORGANIZATION', 'SERVICE'));
--rollback ALTER TABLE capability_restrictions DROP CONSTRAINT ck_capability_restrictions_principal;
--rollback ALTER TABLE capability_restrictions ADD CONSTRAINT ck_capability_restrictions_principal CHECK (principal_type IN ('USER', 'ORGANIZATION', 'SERVICE'));
