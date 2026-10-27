CREATE TABLE oauth_login_attempt (
    state_hash VARCHAR(64) PRIMARY KEY,
    browser_binding_hash VARCHAR(64) NOT NULL,
    provider VARCHAR(10) NOT NULL,
    redirect_uri VARCHAR(2048) NOT NULL,
    nonce VARCHAR(43),
    anonymous_member_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_oauth_login_attempt_provider CHECK (provider IN ('KAKAO', 'APPLE')),
    CONSTRAINT ck_oauth_login_attempt_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_oauth_login_attempt_hashes CHECK (length(state_hash) = 64 AND length(browser_binding_hash) = 64),
    CONSTRAINT ck_oauth_login_attempt_nonce CHECK (
        (provider = 'APPLE' AND nonce IS NOT NULL AND length(nonce) = 43)
        OR (provider = 'KAKAO' AND nonce IS NULL)
    ),
    CONSTRAINT fk_oauth_login_attempt_member FOREIGN KEY (anonymous_member_id) REFERENCES member(id) ON DELETE CASCADE
);
CREATE INDEX idx_oauth_login_attempt_expiry ON oauth_login_attempt(expires_at);
CREATE INDEX idx_oauth_login_attempt_member ON oauth_login_attempt(anonymous_member_id);
