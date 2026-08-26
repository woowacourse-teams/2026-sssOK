-- #69 BaseEntity: 나중에 들어온 stored_file·folder_media 에도 같은 감사 컬럼을 맞춘다.
-- 기존 행은 수정된 적이 없어 updated_at 을 created_at 과 같게 둔다.

ALTER TABLE stored_file ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE stored_file SET updated_at = created_at;
ALTER TABLE stored_file ALTER COLUMN updated_at SET NOT NULL;

-- 담긴 시각을 남기지 않아 기존 행은 알 방법이 없다. 지금 시각으로 채운다.
ALTER TABLE folder_media ADD COLUMN created_at TIMESTAMPTZ;
ALTER TABLE folder_media ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE folder_media SET created_at = now(), updated_at = now();
ALTER TABLE folder_media ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE folder_media ALTER COLUMN updated_at SET NOT NULL;
