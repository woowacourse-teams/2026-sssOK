import type { OrganizeKind, OrganizeProgress } from "../api/types";

/**
 * 분석 서버가 지나가는 단계 순서. 사람별은 `mcp/servers/gallery/pipeline/photo_analysis.py`,
 * 비슷한 사진은 같은 폴더의 `similar_analysis.py` 를 따른다.
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

export const SIMILAR_STAGES = [
  "initializing",
  "preparing",
  "extracting_scene_features",
  "extracting_center_features",
  "matching_similar_photos",
] as const;

const STAGES: Record<OrganizeKind, readonly string[]> = {
  PERSON: PERSON_STAGES,
  SIMILAR: SIMILAR_STAGES,
};

/**
 * 종류를 모르면(다른 기기에서 시작한 작업의 SSE 만 받은 경우) 단계 이름으로 고른다.
 * 앞의 두 단계는 둘이 같아 사람별로 본다. 곧 종류를 아는 응답이 오면 바로잡힌다.
 */
const stagesOf = (kind: OrganizeKind | null, stage: string): readonly string[] => {
  if (kind) return STAGES[kind];
  return STAGES.SIMILAR.slice(2).includes(stage) ? STAGES.SIMILAR : STAGES.PERSON;
};

/** 끝나기 전에는 100% 를 보이지 않는다. 마지막 단계를 다 해도 결과를 받기 전까지는 99% 다. */
const MAX_RUNNING_PERCENT = 99;

/**
 * 진행 정보를 0~99 의 전체 진행률로 바꾼다. 모르는 단계면 null 이다(이전 값을 유지하라는 뜻).
 * `previous` 보다 작아지지 않는다. 진행 바는 앞으로만 간다.
 */
export const overallPercentOf = (
  progress: OrganizeProgress | undefined,
  previous: number | null,
  kind: OrganizeKind | null = "PERSON",
): number | null => {
  if (!progress?.stage) return previous;
  const stages = stagesOf(kind, progress.stage);
  const index = stages.indexOf(progress.stage);
  if (index < 0) return previous;

  const { processed, total } = progress;
  const inStage =
    processed !== null && processed !== undefined && total ? Math.min(1, processed / total) : 0;
  const percent = Math.min(
    MAX_RUNNING_PERCENT,
    Math.floor(((index + inStage) / stages.length) * 100),
  );

  return previous === null ? percent : Math.max(previous, percent);
};
