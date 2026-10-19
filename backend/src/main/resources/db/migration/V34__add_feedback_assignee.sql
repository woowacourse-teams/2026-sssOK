-- #558 의견 담당자. 아직 맡은 사람이 없으면 NULL.
-- 처리 상태에 추가된 IN_PROGRESS·ON_HOLD 는 기존 status 컬럼(VARCHAR(20))에 그대로 들어간다.
ALTER TABLE feedback ADD COLUMN assignee VARCHAR(20);
