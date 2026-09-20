/**
 * API 베이스 URL. backend WebConfig 가 붙이는 /api/v1 까지 포함한다.
 *
 * 기본값은 실제 서버다. 개발 서버에서도 그대로 붙는다 — 서버가 이미
 * `http://localhost:3000` 을 CORS 로 허용해 두었다.
 * 목을 어디까지 씌울지는 `MOCK_MODE` 가 따로 정한다 (`shared/config/mock.ts`).
 *
 * 로컬 백엔드를 보려면 셸에서 넘긴다.
 * 예: `API_BASE_URL=http://localhost:8080/api/v1 MOCK=off pnpm start`
 *
 * 주의: 배포된 프론트는 https 라 `http://` 주소를 넣으면 브라우저가 혼합 콘텐츠로 막는다.
 */
export const API_BASE_URL = process.env.API_BASE_URL ?? "https://api.ssssok.com/api/v1";
