-- A financial transaction (deposit or withdrawal) on an account.
CREATE TABLE transactions (
    id          UUID           PRIMARY KEY,
    account_id  VARCHAR(100)   NOT NULL,
    type        VARCHAR(20)    NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL')),
    amount      NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    occurred_at TIMESTAMPTZ    NOT NULL,
    -- Where the transaction is in its life: PENDING when it is created, then PROCESSING, and finally
    -- COMPLETED or FAILED. There is no default, so every insert has to state its status.
    status      VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

-- Typical access pattern: the transactions of an account over time.
CREATE INDEX idx_transactions_account_id_occurred_at ON transactions (account_id, occurred_at);

-- The table is captured by Debezium (see docs/debezium.md).
-- REPLICA IDENTITY FULL makes PostgreSQL write the whole old row to the WAL on UPDATE and DELETE, so the change
-- events carry a complete "before" image. The default only logs the primary key of the old row.
ALTER TABLE transactions REPLICA IDENTITY FULL;
