-- Where a transaction is in its life: PENDING when it is created, then PROCESSING, and finally COMPLETED or FAILED.
-- Rows that existed before this column were created by the previous flow and are final, so they become COMPLETED.
-- The default only fills those rows. It is dropped right after, so every new insert has to state its status.
ALTER TABLE transactions ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));
ALTER TABLE transactions ALTER COLUMN status DROP DEFAULT;
