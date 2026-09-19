-- #203 의견을 남길 때의 프론트 버전 태그(예: fe-v0.1.0).
--
-- 프론트가 X-App-Version 헤더로 직접 보낸다. User-Agent 와 달리 브라우저가 자동으로 붙여 주지
-- 않아서 빠질 여지가 크고, 부가 정보 때문에 의견 등록이 막히면 안 되므로 NULL 을 허용한다.
ALTER TABLE feedback ADD COLUMN app_version VARCHAR(32);
