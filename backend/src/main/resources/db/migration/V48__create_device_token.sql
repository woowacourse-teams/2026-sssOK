-- 네이티브 앱(iOS·Android) 기기의 FCM 등록 토큰. 앱 설치마다 토큰이 하나라 토큰으로 유일하게 식별한다.
-- 같은 기기에서 다른 계정으로 다시 등록하면 member_id 만 바꿔 끼운다.
CREATE TABLE device_token (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    token VARCHAR(512) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_device_token_token UNIQUE (token),
    CONSTRAINT fk_device_token_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE NO ACTION
);
CREATE INDEX idx_device_token_member ON device_token(member_id);
