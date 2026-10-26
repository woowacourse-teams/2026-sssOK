-- 기존 연결은 재로그인 시 채운다. 제공자 토큰 원문은 저장하지 않는다.
ALTER TABLE social_account
    ADD COLUMN encrypted_access_token TEXT,
    ADD COLUMN encrypted_refresh_token TEXT,
    ADD CONSTRAINT ck_social_account_token_pair
        CHECK (encrypted_refresh_token IS NULL OR encrypted_access_token IS NOT NULL),
    ADD CONSTRAINT ck_social_account_access_cipher
        CHECK (encrypted_access_token IS NULL OR encrypted_access_token LIKE 'v1:%'),
    ADD CONSTRAINT ck_social_account_refresh_cipher
        CHECK (encrypted_refresh_token IS NULL OR encrypted_refresh_token LIKE 'v1:%');
