-- #500 의견의 반영 상태(UNREFLECTED/REFLECTED).
--
-- 기존 의견은 반영 여부를 알 수 없으므로 모두 미반영으로 채운다.
ALTER TABLE feedback ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'UNREFLECTED';
