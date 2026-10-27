CREATE TABLE auth_session (
    id VARCHAR(64) PRIMARY KEY,
    member_id BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_auth_session_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
);
CREATE INDEX idx_auth_session_member ON auth_session(member_id);
CREATE INDEX idx_auth_session_expiry ON auth_session(expires_at);

CREATE TABLE legacy_auth_revocation (
    member_id BIGINT PRIMARY KEY,
    CONSTRAINT fk_legacy_auth_revocation_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE CASCADE
);
