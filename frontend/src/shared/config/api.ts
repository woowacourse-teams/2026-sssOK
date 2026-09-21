/**
 * API 베이스 URL. backend WebConfig 가 붙이는 /api/v1 까지 포함한다.
 *
 * **빌드 모드로 기본값이 갈린다.** 개발은 dev 서버, 배포 빌드는 운영이다.
 * 셸에서 `API_BASE_URL` 을 넘기면 어느 쪽이든 그 값이 이긴다.
 *
 * 개발 기본값이 운영이면 안 되는 이유는 두 가지다. 로컬에서 실수로 운영 데이터를 건드릴 수
 * 있고, 애초에 운영은 `CORS_ALLOWED_ORIGINS` 에 `https://www.ssssok.com` 만 있어서
 * `http://localhost:3000` 으로 붙으면 프리플라이트가 403 `Invalid CORS request` 로 막힌다.
 * dev 서버는 `http://localhost:3000` 을 허용한다.
 *
 * 반대로 배포 빌드가 dev 를 보면 안 되는 이유도 있다. dev 서버가 `http` 라, https 로 서빙되는
 * 배포 프론트에서 부르면 브라우저가 혼합 콘텐츠로 막는다. 그래서 값을 하나로 둘 수 없다.
 *
 * ```
 * pnpm start                                              # dev 서버
 * pnpm start:mock                                         # msw 가 가로채서 나가지 않는다
 * API_BASE_URL=http://localhost:8080/api/v1 pnpm start    # 로컬 백엔드
 * pnpm build                                              # 운영
 * ```
 *
 * dev 서버 API 문서: http://dev-api.ssssok.com/swagger-ui/index.html
 *
 * 없는 경로를 부르면 404 가 아니라 500 `INTERNAL_SERVER_ERROR` 가 온다. 아직 배포되지 않은
 * API 를 부른 것과 서버 오류가 같아 보이니, 의심되면 위 Swagger 에서 경로부터 확인한다.
 */
const DEFAULT_BASE_URL =
  process.env.NODE_ENV === "production"
    ? "https://api.ssssok.com/api/v1"
    : "http://dev-api.ssssok.com/api/v1";

export const API_BASE_URL = process.env.API_BASE_URL ?? DEFAULT_BASE_URL;
