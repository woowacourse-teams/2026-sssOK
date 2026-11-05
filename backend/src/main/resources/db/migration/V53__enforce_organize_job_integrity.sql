-- #659 사진 정리 스키마와 엔티티 제약을 맞춘다. 기존 V51은 변경하지 않는다.
-- 회원·방·사진은 삭제 후 이력 ID를 보존하므로 외부 FK를 생략한다.
-- 조건부 만료 인덱스 2개는 V51의 PostgreSQL 전용 정의를 유지한다.
-- 작업 내부 참조는 NO ACTION: 참조가 남은 작업의 물리 삭제를 거절한다.
ALTER TABLE organize_job ADD CONSTRAINT ck_organize_job_status CHECK (status IN ('QUEUED','RUNNING','READY','FAILED','CANCELED','APPLIED','EXPIRED'));
ALTER TABLE organize_job ADD CONSTRAINT ck_organize_job_kind CHECK (kind IN ('PERSON','SIMILAR'));
ALTER TABLE organize_job ADD CONSTRAINT ck_organize_job_trigger CHECK (trigger_type IN ('BUTTON','TEXT'));
ALTER TABLE organize_job ADD CONSTRAINT ck_organize_job_counts CHECK (photo_count > 0 AND excluded_count >= 0 AND job_version > 0 AND retry_count >= 0);
ALTER TABLE organize_job ADD CONSTRAINT ck_organize_job_active_room CHECK ((status IN ('QUEUED','RUNNING','READY') AND active_room_id IS NOT NULL AND active_room_id = room_id) OR (status IN ('FAILED','CANCELED','APPLIED','EXPIRED') AND active_room_id IS NULL));
ALTER TABLE organize_execution ADD CONSTRAINT ck_organize_execution_status CHECK (status IN ('DISPATCH_PENDING','DISPATCHING','DISPATCH_UNKNOWN','ACCEPTED','RUNNING','COMPLETED','FAILED','CANCELED'));
ALTER TABLE organize_execution ADD CONSTRAINT ck_organize_execution_counts CHECK (attempt > 0 AND dispatch_attempts >= 0);
ALTER TABLE organize_execution ADD CONSTRAINT fk_organize_execution_job FOREIGN KEY (job_id) REFERENCES organize_job(id) ON DELETE NO ACTION;
ALTER TABLE organize_job_photo ADD CONSTRAINT ck_organize_job_photo_source CHECK (source IN ('PREVIEW','ORIGINAL'));
ALTER TABLE organize_job_photo ADD CONSTRAINT fk_organize_job_photo_job FOREIGN KEY (job_id) REFERENCES organize_job(id) ON DELETE NO ACTION;
ALTER TABLE organize_trial ADD CONSTRAINT fk_organize_trial_job FOREIGN KEY (job_id) REFERENCES organize_job(id) ON DELETE NO ACTION;
ALTER TABLE organize_idempotency ADD CONSTRAINT ck_organize_idempotency_operation CHECK (operation IN ('START','RETRY','APPLY'));
ALTER TABLE organize_idempotency ADD CONSTRAINT ck_organize_idempotency_hash CHECK (length(request_hash) = 64);
ALTER TABLE organize_idempotency ADD CONSTRAINT fk_organize_idempotency_job FOREIGN KEY (job_id) REFERENCES organize_job(id) ON DELETE NO ACTION;
ALTER TABLE organize_room_state ADD CONSTRAINT ck_organize_room_state_version CHECK (version > 0);
