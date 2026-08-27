/**
 * MSW 목을 어디까지 켤지 정한다.
 *
 * - `full`   — 모든 요청을 목이 답한다. 서버가 죽었거나 오프라인일 때, 그리고 목만 고칠 때 쓴다.
 * - `hybrid` — 실서버에 붙되 **미디어 목록 조회만** 목이 답한다.
 *              백엔드에 `GET /rooms/{roomId}/media` 가 아직 없어서 갤러리가 설 자리가 없다.
 *              그 한 구멍만 목으로 메우고 나머지 여섯 개는 진짜 서버로 나간다.
 * - `off`    — 목을 아예 띄우지 않는다. 배포 빌드가 이것이다.
 *
 * 기본값은 개발 서버에서 `hybrid`, 그 밖에서는 `off` 다.
 * 바꾸려면 셸에서 넘긴다 (webpack DefinePlugin 이 빌드 시점에 값으로 박는다).
 * 예: `MOCK=full pnpm start`
 */
export type MockMode = "full" | "hybrid" | "off";

const isMockMode = (value: string | undefined): value is MockMode =>
  value === "full" || value === "hybrid" || value === "off";

/** 배포 빌드는 이 값과 무관하게 목을 띄우지 않는다 — `index.tsx` 가 NODE_ENV 로 먼저 끊는다. */
export const MOCK_MODE: MockMode = isMockMode(process.env.MOCK) ? process.env.MOCK : "hybrid";
