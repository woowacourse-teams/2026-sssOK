-- 이전에 소비된 토큰은 시각·후속 토큰 정보가 없으므로 기존 재사용 차단 정책을 유지한다.
ALTER TABLE refresh_token ADD COLUMN used_at TIMESTAMPTZ;
ALTER TABLE refresh_token ADD COLUMN replacement_token_hash VARCHAR(64);
