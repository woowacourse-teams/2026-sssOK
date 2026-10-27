-- 기존 사용자는 익명 상태로 유지한다. 통합·탈퇴한 식별자는 재활성화하지 않는다.
ALTER TABLE member ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ANONYMOUS';
ALTER TABLE member ADD CONSTRAINT ck_member_status
    CHECK (status IN ('ANONYMOUS', 'REGISTERED', 'MERGED', 'WITHDRAWN'));
