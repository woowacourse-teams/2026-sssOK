import type { AdminFeedback } from "../model/types";

/** `fe-v0.1.0` 에서 `[0, 1, 0]` 을 꺼낸다. 형식이 다르면 null. */
const readVersionNumbers = (version: string): number[] | null => {
  const matched = /(\d+)\.(\d+)\.(\d+)/.exec(version);

  if (!matched) return null;

  return [Number(matched[1]), Number(matched[2]), Number(matched[3])];
};

/**
 * 최신 버전이 앞에 오도록 견준다.
 *
 * 문자열 비교로는 `fe-v0.10.0` 이 `fe-v0.9.0` 보다 앞선다고 나온다 — 자리수를 못 본다.
 * 그래서 숫자로 쪼개 견주고, 형식이 다른 값은 뒤로 몰아 이름순으로 둔다.
 */
const compareVersionsDesc = (a: string, b: string): number => {
  const left = readVersionNumbers(a);
  const right = readVersionNumbers(b);

  if (left === null && right === null) return a.localeCompare(b);
  if (left === null) return 1;
  if (right === null) return -1;

  for (let index = 0; index < left.length; index += 1) {
    if (left[index] !== right[index]) return right[index] - left[index];
  }

  return 0;
};

/**
 * 불러온 의견에 실제로 들어 있는 프론트 버전만 모은다 (최신순).
 *
 * 버전 목록을 따로 받아오지 않고 목록에서 뽑는 건, 서버에 그런 API 가 없기 때문이다.
 * 그래서 이 값은 **이미 불러온 페이지 기준**이다 — 다음 페이지를 불러오면 늘어날 수 있다.
 */
export const collectFrontendVersions = (feedbacks: AdminFeedback[]): string[] => {
  const versions = new Set<string>();

  feedbacks.forEach(({ frontendVersion }) => {
    if (frontendVersion !== null) versions.add(frontendVersion);
  });

  return [...versions].sort(compareVersionsDesc);
};
