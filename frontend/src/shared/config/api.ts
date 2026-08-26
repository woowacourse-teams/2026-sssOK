/**
 * API 베이스 URL. backend WebConfig 가 붙이는 /api/v1 까지 포함한다.
 * 기본값은 상대경로라 프론트와 같은 오리진으로 나간다 — 개발 중에는 MSW 가 그 요청을 가로챈다.
 * 개발 서버(pnpm start)에서는 이 값을 줘도 목이 먼저 가로챈다. 목 핸들러도 같은 상수를 쓰기 때문이다.
 * 실제 서버를 보려면 목이 꺼지는 프로덕션 빌드로 확인한다.
 * (예: API_BASE_URL=http://localhost:8080/api/v1 pnpm build)
 */
export const API_BASE_URL = process.env.API_BASE_URL ?? "/api/v1";
