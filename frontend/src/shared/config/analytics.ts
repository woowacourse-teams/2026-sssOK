/**
 * PostHog 설정. 프로젝트 키는 브라우저에 그대로 노출되는 공개 키라 코드에 둔다.
 * 다른 프로젝트로 보내 보고 싶으면 셸에서 `POSTHOG_KEY`, `POSTHOG_HOST` 로 덮어쓴다.
 *
 * 수집은 배포 빌드에서만 한다 — `shared/lib/analytics.ts` 가 NODE_ENV 로 먼저 끊는다.
 * 로컬·목 환경의 클릭이 운영 지표에 섞이면 안 되기 때문이다.
 */
export const POSTHOG_KEY =
  process.env.POSTHOG_KEY ?? "phc_tJEUFUieM5VwSqS8BwfHTc3feYThtU9sa92zimfjMigd";

/** 가입할 때 고른 US 리전의 수집 주소. 대시보드 주소(us.posthog.com)와 다르다. */
export const POSTHOG_HOST = process.env.POSTHOG_HOST ?? "https://us.i.posthog.com";
