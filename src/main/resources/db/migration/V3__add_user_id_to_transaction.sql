-- The user who owns the transaction. Every transaction has one, and a user with transactions cannot be deleted.
-- The column is NOT NULL and has no default, so this migration only runs while the transactions table is empty.
ALTER TABLE transactions ADD COLUMN user_id UUID NOT NULL REFERENCES users (id);

-- Typical access pattern: the transactions of a user over time.
CREATE INDEX idx_transactions_user_id_occurred_at ON transactions (user_id, occurred_at);
