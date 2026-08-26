-- #48 미디어 하나가 여러 폴더에 동시에 속할 수 있어 다대다로 관리한다.
-- 순수 인프라 조인 테이블이라 도메인 계층 없이 folder_id/media_id만 남긴다 — 담기/꺼내기 기능에서 JPA로 직접 다룬다.
CREATE TABLE folder_media (
    id        BIGSERIAL PRIMARY KEY,
    folder_id BIGINT NOT NULL,
    media_id  BIGINT NOT NULL,
    CONSTRAINT uk_folder_media UNIQUE (folder_id, media_id)
);
