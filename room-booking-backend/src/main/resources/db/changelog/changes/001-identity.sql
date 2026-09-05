--liquibase formatted sql

--changeset ninggiangboy:001-01-users
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               CITEXT NOT NULL,
    phone_number        VARCHAR(32),
    password_hash       VARCHAR(255) NOT NULL,
    display_name        VARCHAR(120) NOT NULL,
    avatar_url          VARCHAR(2048),
    status              VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    email_verified_at   TIMESTAMPTZ,
    phone_verified_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone_number UNIQUE (phone_number),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT ck_users_version CHECK (version >= 0)
);
--rollback DROP TABLE users;

--changeset ninggiangboy:001-02-user-roles
CREATE TABLE user_roles (
    user_id      UUID NOT NULL,
    role         VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_roles_role CHECK (role IN ('GUEST', 'HOST', 'ADMIN'))
);
--rollback DROP TABLE user_roles;

--changeset ninggiangboy:001-03-host-profiles
CREATE TABLE host_profiles (
    user_id             UUID PRIMARY KEY,
    bio                 TEXT,
    identity_status     VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED',
    average_rating      NUMERIC(3, 2),
    review_count        INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_host_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_host_profiles_identity_status CHECK (
        identity_status IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED')
    ),
    CONSTRAINT ck_host_profiles_average_rating CHECK (
        average_rating IS NULL OR average_rating BETWEEN 1.00 AND 5.00
    ),
    CONSTRAINT ck_host_profiles_review_count CHECK (review_count >= 0),
    CONSTRAINT ck_host_profiles_version CHECK (version >= 0)
);
--rollback DROP TABLE host_profiles;
