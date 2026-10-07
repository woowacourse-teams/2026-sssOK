-- #460 모델 확정 전에는 차원을 고정하지 않는다. 모델·차원은 문서에 함께 저장한다.
CREATE TABLE media_search_document (
    media_id BIGINT PRIMARY KEY REFERENCES stored_file(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    description TEXT,
    features TEXT,
    search_text TEXT,
    embedding vector,
    embedding_model VARCHAR(100),
    embedding_dimensions INT,
    analysis_model VARCHAR(100),
    prompt_version VARCHAR(100),
    attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (status <> 'READY' OR (
        description IS NOT NULL AND features IS NOT NULL AND search_text IS NOT NULL
        AND embedding IS NOT NULL AND embedding_model IS NOT NULL
        AND analysis_model IS NOT NULL AND prompt_version IS NOT NULL
        AND embedding_dimensions IS NOT NULL AND embedding_dimensions > 0
        AND vector_dims(embedding) = embedding_dimensions
        AND completed_at IS NOT NULL))
);
CREATE INDEX idx_media_search_document_pending
    ON media_search_document (next_attempt_at, media_id) WHERE status = 'PENDING';
CREATE INDEX idx_media_search_document_processing
    ON media_search_document (started_at, media_id) WHERE status = 'PROCESSING';
