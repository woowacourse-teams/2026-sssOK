-- #24 Room 도메인 확장(이름/만료/업로드권한/방장/삭제시각)에 맞춰 컬럼을 추가한다.
-- V1은 이미 적용된 마이그레이션이라 수정하지 않고 새 버전으로 쌓는다.
ALTER TABLE room
    ADD COLUMN name          VARCHAR(30)  NOT NULL DEFAULT '',
    ADD COLUMN expires_at    TIMESTAMPTZ  NOT NULL DEFAULT (now() + interval '24 hours'),
    ADD COLUMN upload_policy VARCHAR(20)  NOT NULL DEFAULT 'ANYONE',
    ADD COLUMN host_id       BIGINT       NOT NULL DEFAULT 0,
    ADD COLUMN deleted_at    TIMESTAMPTZ;

-- 기본값은 기존 행을 채우기 위한 것일 뿐, 이후로는 애플리케이션이 항상 값을 채운다.
ALTER TABLE room ALTER COLUMN name DROP DEFAULT;
ALTER TABLE room ALTER COLUMN expires_at DROP DEFAULT;
ALTER TABLE room ALTER COLUMN upload_policy DROP DEFAULT;
ALTER TABLE room ALTER COLUMN host_id DROP DEFAULT;
