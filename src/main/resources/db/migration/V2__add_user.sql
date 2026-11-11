-- A person who owns transactions.
CREATE TABLE users (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    email      VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);

-- An email identifies one user, regardless of upper or lower case.
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));
