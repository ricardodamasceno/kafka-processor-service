-- Where a user is in their life: ACTIVE after the registration, INACTIVE when the account is no longer in use,
-- BLOCKED when it is suspended (for example after a fraud alert).
-- Users that existed before this column were registered by the previous flow and are active.
-- The default only fills those rows. It is dropped right after, so every new insert has to state its status.
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'));
ALTER TABLE users ALTER COLUMN status DROP DEFAULT;
