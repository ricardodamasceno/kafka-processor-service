-- A movement of money in the account of a user, caused by a transaction: a CREDIT adds money, a DEBIT takes it.
-- The amount is always positive; the direction comes from the type. Movements are only ever inserted.
CREATE TABLE movements (
    id             UUID           PRIMARY KEY,
    user_id        UUID           NOT NULL REFERENCES users (id),
    transaction_id UUID           NOT NULL REFERENCES transactions (id),
    amount         NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    type           VARCHAR(10)    NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    created_at     TIMESTAMPTZ    NOT NULL
);

-- A transaction causes at most one movement of each type. The event that triggers a movement is delivered at least
-- once, so without this a repeated delivery would move the money twice.
CREATE UNIQUE INDEX uq_movements_transaction_id_type ON movements (transaction_id, type);

-- Typical access pattern: the movements of a user over time.
CREATE INDEX idx_movements_user_id_created_at ON movements (user_id, created_at);
