CREATE TABLE room (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(8) NOT NULL UNIQUE,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
