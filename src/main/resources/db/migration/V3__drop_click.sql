-- Clicks are no longer part of this service.
-- V1 stays in the history because an applied migration must never be edited; this one removes what it created.
-- The index on (ad_id, clicked_at) is dropped together with the table.
DROP TABLE IF EXISTS clicks;
