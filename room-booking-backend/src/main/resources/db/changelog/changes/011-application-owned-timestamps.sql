--liquibase formatted sql

-- The application clock is the single source of time for the rows it writes. Spring Data JDBC
-- auditing populates created_at/updated_at from the shared UTC Clock, so a database-side
-- DEFAULT now() would be a second, unsynchronised clock: it records transaction-start time on the
-- database host, and it silently hides an insert path that forgot to supply the value. Removing the
-- default makes such a path fail loudly instead of producing timestamps the domain never decided.
-- Tables not written by the application keep their defaults until they gain an owning aggregate.

--changeset ninggiangboy:011-01-drop-application-timestamp-defaults
ALTER TABLE users ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE users ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE user_roles ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE host_profiles ALTER COLUMN created_at DROP DEFAULT;
ALTER TABLE host_profiles ALTER COLUMN updated_at DROP DEFAULT;
ALTER TABLE auth_tokens ALTER COLUMN created_at DROP DEFAULT;
--rollback ALTER TABLE auth_tokens ALTER COLUMN created_at SET DEFAULT now();
--rollback ALTER TABLE host_profiles ALTER COLUMN updated_at SET DEFAULT now();
--rollback ALTER TABLE host_profiles ALTER COLUMN created_at SET DEFAULT now();
--rollback ALTER TABLE user_roles ALTER COLUMN created_at SET DEFAULT now();
--rollback ALTER TABLE users ALTER COLUMN updated_at SET DEFAULT now();
--rollback ALTER TABLE users ALTER COLUMN created_at SET DEFAULT now();
