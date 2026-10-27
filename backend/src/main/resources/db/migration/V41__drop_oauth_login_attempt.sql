-- 로그인 시도는 단일 서버 메모리 캐시로 관리한다.
-- V39 적용 이력은 유지하고 임시 로그인 시도 테이블만 제거한다.
DROP TABLE IF EXISTS oauth_login_attempt;
