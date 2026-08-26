/**
 * API 베이스 URL. backend WebConfig 가 붙이는 /api/v1 까지 포함한다.
 * 기본값은 상대경로라 프론트와 같은 오리진으로 나간다 — 개발 중에는 MSW 가 그 요청을 가로챈다.
 * 다른 오리진의 서버를 보려면 API_BASE_URL 을 주고 빌드한다.
 * (예: API_BASE_URL=http://localhost:8080/api/v1 pnpm start)
 */
export const API_BASE_URL = process.env.API_BASE_URL ?? "/api/v1";
