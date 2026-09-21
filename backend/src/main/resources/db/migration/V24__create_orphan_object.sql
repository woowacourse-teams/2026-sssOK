-- 주인이 사라진 스토리지 오브젝트의 정리 대기열.
--
-- 미디어 삭제는 DB 행을 지우는 트랜잭션 안에서 이 행을 함께 남기고, 실제 오브젝트 정리는
-- 커밋 뒤 비동기로 한다. 정리가 성공하면 행이 사라지고, 실패하거나 그 사이 서버가 죽으면
-- 행이 남아 OrphanObjectSweeper 가 다시 태운다.
--
-- 이 대기열이 없으면 정리를 트랜잭션 밖으로 빼는 순간 실패한 오브젝트를 되찾을 방법이 없다.
-- 스토리지를 prefix 로 훑어 DB 와 대조하는 방법은 아직 업로드 중인 RESERVED 미디어의 키와
-- 고아를 구분하지 못해 쓸 수 없다.
--
-- storage_key 에 UNIQUE 를 걸지 않는다. 같은 키가 두 번 들어와도 삭제는 멱등이라 해가 없고,
-- UNIQUE 를 걸면 중복 회피에 PostgreSQL 전용 ON CONFLICT 가 필요해져 H2 테스트에서 검증할 수 없다.
CREATE TABLE orphan_object (
    id                BIGSERIAL PRIMARY KEY,
    storage_key       VARCHAR(255) NOT NULL,
    attempts          INT NOT NULL DEFAULT 0,
    last_attempted_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

-- 회수 배치가 "오래 남아 있고 최근에 시도하지 않은 것부터" 집는 순서 그대로다.
-- 한 번도 시도하지 않은 행(NULL)이 먼저 와야 비동기 정리가 통째로 유실된 경우를 가장 빨리 메운다.
CREATE INDEX idx_orphan_object_sweep
    ON orphan_object (last_attempted_at NULLS FIRST, id);
