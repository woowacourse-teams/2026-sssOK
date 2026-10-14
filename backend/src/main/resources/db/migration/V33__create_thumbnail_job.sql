-- 작업 기록은 사진 등록과 같은 트랜잭션에서 저장한다. 미디어 삭제 후에도 처리 이력을 보존한다.
CREATE TABLE thumbnail_job (
    media_id BIGINT PRIMARY KEY,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('IMAGE', 'VIDEO')),
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL,
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(100),
    thumbnail_key VARCHAR(255),
    preview_key VARCHAR(255),
    CONSTRAINT thumbnail_job_claim CHECK (
        (state = 'RUNNING' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (state <> 'RUNNING' AND claim_token IS NULL AND lease_until IS NULL)
    )
);
CREATE INDEX idx_thumbnail_job_dispatch ON thumbnail_job(kind, available_at, media_id)
    WHERE state IN ('PENDING', 'RETRY_WAIT');
CREATE INDEX idx_thumbnail_job_lease ON thumbnail_job(lease_until, media_id) WHERE state = 'RUNNING';
