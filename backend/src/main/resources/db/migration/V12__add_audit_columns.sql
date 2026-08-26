-- #69 BaseEntity: 생성·수정 시각을 모든 테이블에 같은 이름으로 둔다.
-- 기존 행은 의미가 가장 가까운 값으로 채운다. 수정된 적이 없으므로 updated_at 은 생성 시각과 같다.

ALTER TABLE room ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE room SET updated_at = created_at;
ALTER TABLE room ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE member ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE member SET updated_at = created_at;
ALTER TABLE member ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE room_events ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE room_events SET updated_at = created_at;
ALTER TABLE room_events ALTER COLUMN updated_at SET NOT NULL;

-- 입장 기록은 joined_at 이 곧 만들어진 시각이다.
ALTER TABLE room_member ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE room_member ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE room_member SET created_at = joined_at, updated_at = joined_at;
ALTER TABLE room_member ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE room_member ALTER COLUMN updated_at SET NOT NULL;

-- 연결 코드는 만들어진 시각을 남기지 않았다. 5분 TTL 이라 만료 시각에서 거꾸로 잡는다.
ALTER TABLE link_code ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE link_code ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE link_code SET created_at = expires_at - INTERVAL '5 minutes', updated_at = expires_at - INTERVAL '5 minutes';
ALTER TABLE link_code ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE link_code ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE folder ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE folder SET updated_at = created_at;
ALTER TABLE folder ALTER COLUMN updated_at SET NOT NULL;
