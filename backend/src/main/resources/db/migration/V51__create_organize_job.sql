-- #659 사진 정리 작업. 사용자가 방에서 한 번 요청한 정리(job)와 Python 분석 1회(execution), 요청 시점의 대상 사진을 저장한다.
-- 사진·회원이 지워져도 작업 기록(상태·사유)은 남겨야 해서 stored_file, member 에 FK 를 걸지 않는다.
CREATE TABLE organize_job (
    id                 BIGSERIAL PRIMARY KEY,
    public_id          UUID NOT NULL,
    room_id            BIGINT NOT NULL,
    requester_id       BIGINT NOT NULL,
    kind               VARCHAR(16) NOT NULL,
    trigger_type       VARCHAR(16) NOT NULL,
    status             VARCHAR(16) NOT NULL,
    request_text       VARCHAR(1000),
    server_id          VARCHAR(64) NOT NULL,
    tool_name          VARCHAR(128) NOT NULL,
    -- 선택 당시의 도구 설명. 발송 때 MCP 의 현재 도구와 같은지 비교해 선택 이후 바뀐 도구를 거른다.
    tool_description   TEXT NOT NULL,
    photo_count        INT NOT NULL,
    excluded_count     INT NOT NULL,
    -- 방을 점유하는 동안(QUEUED·RUNNING·READY)만 방 ID 이고 종료되면 NULL 이다.
    -- 유니크라서 방당 활성 작업 1개를 DB 가 보장한다.
    active_room_id     BIGINT,
    -- 오래된 SSE·스냅샷을 가리기 위한 갱신 버전. 상태나 진행이 바뀔 때마다 1씩 올린다.
    job_version        INT NOT NULL DEFAULT 1,
    -- 새 실행 회차를 만든 사용자 재시도 횟수. 상한은 설정으로 둔다.
    retry_count        INT NOT NULL DEFAULT 0,
    failure_code       VARCHAR(48),
    failure_detail     VARCHAR(500),
    result_version     INT,
    result             TEXT,
    progress_stage     VARCHAR(48),
    progress_processed INT,
    progress_total     INT,
    -- 분석 완료(READY) 시각 기준 검토 기한과 결과 본문 보관 기한. 서로 독립이다.
    review_expires_at  TIMESTAMPTZ,
    result_expires_at  TIMESTAMPTZ,
    -- 종료 상태(FAILED·CANCELED·APPLIED·EXPIRED)가 된 시각.
    finished_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_organize_job_public_id UNIQUE (public_id),
    CONSTRAINT uk_organize_job_active_room UNIQUE (active_room_id)
);
CREATE INDEX idx_organize_job_requester_room ON organize_job(requester_id, room_id, id DESC);
CREATE INDEX idx_organize_job_review_expires ON organize_job(review_expires_at) WHERE status = 'READY';
CREATE INDEX idx_organize_job_result_expires ON organize_job(result_expires_at) WHERE result IS NOT NULL;

CREATE TABLE organize_execution (
    id                   BIGSERIAL PRIMARY KEY,
    job_id               BIGINT NOT NULL,
    execution_id         UUID NOT NULL,
    attempt              INT NOT NULL,
    status               VARCHAR(24) NOT NULL,
    dispatch_attempts    INT NOT NULL,
    next_dispatch_at     TIMESTAMPTZ,
    dispatch_deadline_at TIMESTAMPTZ NOT NULL,
    lease_until          TIMESTAMPTZ,
    accepted_at          TIMESTAMPTZ,
    started_at           TIMESTAMPTZ,
    last_event_at        TIMESTAMPTZ,
    finished_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_organize_execution_execution_id UNIQUE (execution_id),
    CONSTRAINT uk_organize_execution_job_attempt UNIQUE (job_id, attempt)
);
CREATE INDEX idx_organize_execution_status ON organize_execution(status);

CREATE TABLE organize_job_photo (
    id         BIGSERIAL PRIMARY KEY,
    job_id     BIGINT NOT NULL,
    media_id   BIGINT NOT NULL,
    extension  VARCHAR(8) NOT NULL,
    source     VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_organize_job_photo UNIQUE (job_id, media_id)
);

-- 익명 회원의 체험. (회원, 방)마다 한 행만 만들 수 있어서 같은 방의 동시 접수 중 하나만 사용권을 얻는다.
-- 행이 있으면 사용권을 쓴 상태이고, 최종 실패나 접수 전 취소에서는 행을 지워 돌려준다.
CREATE TABLE organize_trial (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL,
    room_id    BIGINT NOT NULL,
    job_id     BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_organize_trial_member_room UNIQUE (member_id, room_id),
    CONSTRAINT uk_organize_trial_job UNIQUE (job_id)
);

-- OAuth 회원의 새 작업 접수 간격. 회원당 한 행을 조건부 UPDATE 해서 동시 접수에서도 한쪽만 통과시킨다.
CREATE TABLE organize_start_gate (
    member_id       BIGINT PRIMARY KEY,
    last_started_at TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

-- 같은 요청의 재전송을 가려내는 멱등 기록. 성공한 요청만 남기고 보관 기간이 지나면 지운다.
CREATE TABLE organize_idempotency (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT NOT NULL,
    room_id      BIGINT NOT NULL,
    operation    VARCHAR(16) NOT NULL,
    idem_key     VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    job_id       BIGINT,
    response     TEXT,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_organize_idempotency UNIQUE (member_id, room_id, operation, idem_key)
);
CREATE INDEX idx_organize_idempotency_created ON organize_idempotency(created_at);

-- 방 작업 점유 상태의 증가 버전. 점유가 바뀔 때(접수·종료)만 올라서 화면이 오래된 알림을 가릴 수 있다.
CREATE TABLE organize_room_state (
    room_id    BIGINT PRIMARY KEY,
    version    BIGINT NOT NULL,
    busy       BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
