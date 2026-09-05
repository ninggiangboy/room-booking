--liquibase formatted sql

--changeset ninggiangboy:000-01-platform-extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS btree_gist;
--rollback DROP EXTENSION IF EXISTS btree_gist;
--rollback DROP EXTENSION IF EXISTS citext;
--rollback DROP EXTENSION IF EXISTS pgcrypto;
