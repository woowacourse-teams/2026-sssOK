CREATE TABLE social_account (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    provider VARCHAR(10) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_social_account_member FOREIGN KEY (member_id) REFERENCES member(id),
    CONSTRAINT uk_social_account_identity UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_social_account_provider CHECK (provider IN ('KAKAO', 'APPLE')),
    CONSTRAINT ck_social_account_subject CHECK (length(trim(provider_user_id)) > 0)
);
CREATE INDEX idx_social_account_member ON social_account(member_id);
