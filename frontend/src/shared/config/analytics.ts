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

/** 쉼표로 이은 회원 ID 를 읽는다. 숫자가 아닌 칸은 버린다. */
export const parseMemberIds = (raw: string | undefined): number[] =>
  (raw ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter((value) => /^\d+$/.test(value))
    .map(Number);

/**
 * 팀원 회원 ID. 이 계정으로 로그인한 기기는 팀원 기기로 표시해 지표에서 뺀다.
 *
 * 앱 웹뷰는 주소창이 없어 `?internal=1` 로 표시를 켤 수 없어서, 로그인한 계정으로 가린다.
 * 공개 저장소에 회원 ID 를 남기지 않으려고 코드에 두지 않고 빌드 환경 변수
 * `INTERNAL_MEMBER_IDS`(예: `12,34`)로 받는다. 운영 빌드는 CodeBuild 프로젝트 환경 변수에 넣는다.
 */
export const INTERNAL_MEMBER_IDS = parseMemberIds(process.env.INTERNAL_MEMBER_IDS);
