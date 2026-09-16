--liquibase formatted sql

-- D01's target account lifecycle interposes DELETION_REQUESTED between a live status and terminal
-- closure, since a host with future stays, unsettled balances, or open cases cannot become
-- unreachable instantly. Which obligations block completion, and how long the window is, remain
-- open product decisions (D22); this migration only adds the state and the transitions this pass
-- wires: self-service closure now requests deletion rather than closing immediately, and an
-- operator completes it by hand. See
-- docs/implementation/identity/09-roadmap.md#erasure.

--changeset ninggiangboy:042-01-deletion-requested-status
ALTER TABLE account_holders DROP CONSTRAINT ck_account_holders_status;
ALTER TABLE account_holders ADD CONSTRAINT ck_account_holders_status CHECK (
    status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETION_REQUESTED', 'CLOSED')
);
--rollback ALTER TABLE account_holders DROP CONSTRAINT ck_account_holders_status;
--rollback ALTER TABLE account_holders ADD CONSTRAINT ck_account_holders_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'CLOSED'));
