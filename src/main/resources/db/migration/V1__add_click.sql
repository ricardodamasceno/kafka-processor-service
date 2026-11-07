-- A click on an ad, made by a user at a given moment.
CREATE TABLE clicks (
    id         UUID         PRIMARY KEY,
    ad_id      VARCHAR(100) NOT NULL,
    user_id    VARCHAR(100) NOT NULL,
    clicked_at TIMESTAMPTZ  NOT NULL
);

-- Typical access pattern: clicks of an ad over time.
CREATE INDEX idx_clicks_ad_id_clicked_at ON clicks (ad_id, clicked_at);
