-- #670 갤러리 사진 신고. 한 사람이 같은 사진은 한 번만 신고할 수 있다.
-- 신고 기록은 사진이 지워진 뒤에도 관리자가 확인할 수 있어야 해서 stored_file 에 FK 를 걸지 않는다.
CREATE TABLE media_report (
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT NOT NULL,
    media_id    BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    reason      VARCHAR(32) NOT NULL,
    reported_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_media_report UNIQUE (media_id, member_id)
);
CREATE INDEX idx_media_report_member ON media_report(member_id);
