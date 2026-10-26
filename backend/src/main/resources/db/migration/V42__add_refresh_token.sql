ALTER TABLE auth_session ADD COLUMN refresh_token_delivery VARCHAR(10);

CREATE TABLE refresh_token (
    token_hash VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL REFERENCES auth_session(id) ON DELETE CASCADE,
    used BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_refresh_token_session ON refresh_token(session_id);
