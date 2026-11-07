-- A financial transaction (deposit or withdrawal) on an account.
CREATE TABLE transactions (
    id          UUID           PRIMARY KEY,
    account_id  VARCHAR(100)   NOT NULL,
    type        VARCHAR(20)    NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL')),
    amount      NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    occurred_at TIMESTAMPTZ    NOT NULL
);

-- Typical access pattern: the transactions of an account over time.
CREATE INDEX idx_transactions_account_id_occurred_at ON transactions (account_id, occurred_at);
