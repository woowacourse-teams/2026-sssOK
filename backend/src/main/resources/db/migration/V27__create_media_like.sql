-- #448 사진 좋아요. 한 회원은 한 미디어에 한 번만 누를 수 있어 (media_id, member_id) 를 유일하게 둔다.
-- 이 유일 인덱스가 media_id 로 시작하므로 미디어별 개수 집계와 "내가 누른 미디어" 조회도 같은 인덱스를 탄다.
-- folder_media 처럼 순수 조인 관계라 도메인 객체 없이 ID 로만 다룬다.
CREATE TABLE media_like (
    id         BIGSERIAL PRIMARY KEY,
    media_id   BIGINT NOT NULL,
    member_id  BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_media_like UNIQUE (media_id, member_id)
);
