import type { OrganizeProgress } from "../api/types";

/**
 * 사람별로 나누기 분석 서버(`mcp/servers/gallery/pipeline/photo_analysis.py`)가 지나가는 단계 순서.
 *
 * 서버가 주는 `processed / total` 은 **단계마다 0부터 다시 센다.** 단계가 바뀌면 숫자가 되돌아가고,
 * 숫자를 주지 않는 단계(묶기·내보내기)도 있다. 그대로 그리면 진행 바가 앞뒤로 튀거나 비어 보여서,
 * 단계 순서와 단계 안 처리 비율로 전체 진행률을 하나로 만든다. 단계는 같은 몫으로 나눈다.
 */
export const PERSON_STAGES = [
  "initializing",
  "preparing",
  "detecting_faces",
  "grouping_faces",
  "detecting_bodies",
  "extracting_body_features",
  "matching_bodies",
  "exporting",
] as const;

/** 끝나기 전에는 100% 를 보이지 않는다. 마지막 단계를 다 해도 결과를 받기 전까지는 99% 다. */
const MAX_RUNNING_PERCENT = 99;

/**
 * 진행 정보를 0~99 의 전체 진행률로 바꾼다. 모르는 단계면 null 이다(이전 값을 유지하라는 뜻).
 * `previous` 보다 작아지지 않는다. 진행 바는 앞으로만 간다.
 */
export const overallPercentOf = (
  progress: OrganizeProgress | undefined,
  previous: number | null,
): number | null => {
  const index = progress?.stage
    ? PERSON_STAGES.indexOf(progress.stage as (typeof PERSON_STAGES)[number])
    : -1;
  if (index < 0) return previous;

  const { processed, total } = progress ?? {};
  const inStage =
    processed !== null && processed !== undefined && total ? Math.min(1, processed / total) : 0;
  const percent = Math.min(
    MAX_RUNNING_PERCENT,
    Math.floor(((index + inStage) / PERSON_STAGES.length) * 100),
  );

  return previous === null ? percent : Math.max(previous, percent);
};
