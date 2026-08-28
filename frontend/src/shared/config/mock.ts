/**
 * MSW 목을 켤지 정한다.
 *
 * - `full` — 모든 요청을 목이 답한다. 서버가 죽었거나 오프라인일 때, 그리고 목만 고칠 때 쓴다.
 * - `off`  — 목을 아예 띄우지 않고 모든 요청이 실서버로 간다. 기본값이자 배포 빌드다.
 *
 * 바꾸려면 셸에서 넘긴다 (webpack DefinePlugin 이 빌드 시점에 값으로 박는다).
 * 예: `MOCK=full pnpm start` (= `pnpm start:mock`)
 */
export type MockMode = "full" | "off";

const isMockMode = (value: string | undefined): value is MockMode =>
  value === "full" || value === "off";

/** 배포 빌드는 이 값과 무관하게 목을 띄우지 않는다 — `index.tsx` 가 NODE_ENV 로 먼저 끊는다. */
export const MOCK_MODE: MockMode = isMockMode(process.env.MOCK) ? process.env.MOCK : "off";
