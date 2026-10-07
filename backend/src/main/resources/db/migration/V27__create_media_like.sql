-- #448 사진 좋아요
CREATE TABLE media_like (
    id         BIGSERIAL PRIMARY KEY,
    media_id   BIGINT NOT NULL,
    member_id  BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_media_like UNIQUE (media_id, member_id)
);
