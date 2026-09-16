--liquibase formatted sql

-- D01's target account lifecycle starts a self-registered person at PENDING_VERIFICATION, not
-- ACTIVE, since the primary email channel registration creates is unproven until the holder
-- completes the same link-based flow EmailVerificationService already issues. Organizations and
-- every other AccountHolderStatus transition are unaffected: the default stays 'ACTIVE', and only
-- UserRegistrationFactory sets the new value explicitly. See
-- docs/implementation/identity/09-roadmap.md#pending_verification-state.

--changeset ninggiangboy:041-01-pending-verification-status
ALTER TABLE account_holders DROP CONSTRAINT ck_account_holders_status;
ALTER TABLE account_holders ADD CONSTRAINT ck_account_holders_status CHECK (
    status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'CLOSED')
);
--rollback ALTER TABLE account_holders DROP CONSTRAINT ck_account_holders_status;
--rollback ALTER TABLE account_holders ADD CONSTRAINT ck_account_holders_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'));
