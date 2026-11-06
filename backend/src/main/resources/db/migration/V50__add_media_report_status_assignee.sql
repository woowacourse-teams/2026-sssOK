-- #671 신고 처리 상태·담당자, 신고 당시 닉네임·방 이름
-- 닉네임·방 이름은 신고 당시 값을 복사해 둔다. 기존 신고는 NULL 이다.
ALTER TABLE media_report
    ADD COLUMN status    VARCHAR(32) NOT NULL DEFAULT 'UNREFLECTED',
    ADD COLUMN assignee  VARCHAR(32),
    ADD COLUMN nickname  VARCHAR(255),
    ADD COLUMN room_name VARCHAR(255);

CREATE INDEX idx_media_report_reported_at ON media_report(reported_at DESC, id DESC);
