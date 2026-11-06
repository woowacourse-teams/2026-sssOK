-- 진행 처리 수의 단위. 단계마다 센 대상이 다르다(사진 수 / 검출된 신체 수). 수치가 없는 단계는 NULL 이다.
ALTER TABLE organize_job ADD COLUMN progress_unit VARCHAR(16);
ALTER TABLE organize_job ADD CONSTRAINT ck_organize_job_progress_unit CHECK (progress_unit IS NULL OR progress_unit IN ('PHOTO','BODY'));
