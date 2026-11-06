-- 애플 토큰을 발급한 client 설정(웹/앱)을 보관 토큰과 함께 기록한다. 연결 해제는 이 값으로 같은 설정을 고른다.
-- NULL은 이 컬럼이 생기기 전에 저장된 토큰이며 웹 설정으로 취급한다.
ALTER TABLE social_account
    ADD COLUMN token_client_type VARCHAR(10),
    ADD CONSTRAINT ck_social_account_token_client_type
        CHECK (token_client_type IS NULL OR token_client_type IN ('WEB', 'NATIVE'));
