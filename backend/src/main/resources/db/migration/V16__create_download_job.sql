CREATE TABLE download_job (
    id               BIGSERIAL PRIMARY KEY,
    room_id          BIGINT NOT NULL,
    requester_id     BIGINT NOT NULL,
    status           VARCHAR(20) NOT NULL,
    media_count      INT NOT NULL,
    total_size_bytes BIGINT NOT NULL,
    file_name        VARCHAR(255) NOT NULL,
    zip_storage_key  VARCHAR(255),
    progress         INT NOT NULL DEFAULT 0,
    ready_at         TIMESTAMPTZ,
    failure_reason   VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_download_job_room_id ON download_job (room_id);
CREATE INDEX idx_download_job_requester_status ON download_job (requester_id, status);

-- 잡 생성 시점에 압축 대상 media id를 확정해 저장한다.
-- 워커가 나중에 다시 리졸빙하면 그 사이 상태가 바뀐 미디어 때문에
-- 202 응답의 mediaCount와 실제 압축 결과가 어긋날 수 있어, 이를 막기 위함이다.
CREATE TABLE download_job_media (
    id              BIGSERIAL PRIMARY KEY,
    download_job_id BIGINT NOT NULL,
    media_id        BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_download_job_media_job_id ON download_job_media (download_job_id);
