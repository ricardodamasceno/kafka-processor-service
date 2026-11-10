-- The transactions table is captured by Debezium (see docs/debezium.md).
-- REPLICA IDENTITY FULL makes PostgreSQL write the whole old row to the WAL on UPDATE and DELETE, so the change
-- events carry a complete "before" image. The default only logs the primary key of the old row.
ALTER TABLE transactions REPLICA IDENTITY FULL;
