import { isApiError } from "@/shared/api";
import { type AnalyticsRoomContext, track } from "@/shared/lib";
import type { OrganizeLimit } from "./analyzablePhotos";

/** 막힌 까닭의 장수. 체험 소진처럼 장수와 상관없는 까닭이면 null 이다. */
const countOf = (limit: OrganizeLimit, fallback: number | null): number | null =>
  limit.reason === "tooMany" ? (limit.count ?? fallback) : fallback;

/** 정리 버튼을 눌렀다. 갤러리가 센 장수와, 그 자리에서 막혔으면 막힌 까닭을 함께 남긴다. */
export const trackOrganizeOpened = (
  photoCount: number | null,
  limit: OrganizeLimit | null,
): void => {
  track("Photo Organize Opened", { photo_count: photoCount });
  if (limit) {
    track("Photo Organize Blocked", {
      reason: limit.reason,
      photo_count: countOf(limit, photoCount),
      stage: "precheck",
    });
  }
};

/** 서버가 장수나 체험 소진으로 접수를 거절했다. */
export const trackOrganizeRejected = (limit: OrganizeLimit): void => {
  track("Photo Organize Blocked", {
    reason: limit.reason,
    photo_count: countOf(limit, null),
    stage: "server",
  });
};

/** 접수 단계에서 제한이 아닌 까닭으로 실패했다. 오류 코드를 모르면 `unknown` 으로 남긴다. */
export const trackOrganizeStartFailed = (cause: unknown): void => {
  track("Photo Organize Failed", {
    reason: isApiError(cause) ? cause.code : "unknown",
    stage: "start",
  });
};

/**
 * 분석은 몇 분 걸리고 그동안 갤러리를 떠날 수 있다. 끝나는 이벤트는 시작할 때 잡아 둔 방으로 남긴다.
 * 이 기기에서 시작한 작업만 따라간다. 다시 들어와 이어 본 작업까지 세면 완료가 두 번 잡힌다.
 */
export interface StartedOrganize {
  jobId: string;
  kind: "PERSON" | "SIMILAR";
  photoCount: number;
  startedAt: number;
  room: AnalyticsRoomContext | null;
}

export const trackOrganizeStarted = ({ kind, photoCount, room }: StartedOrganize): void => {
  track("Photo Organize Started", { kind, photo_count: photoCount }, room);
};

export const trackOrganizeCompleted = (
  started: StartedOrganize,
  groupCount: number,
  now: number = Date.now(),
): void => {
  track(
    "Photo Organize Completed",
    {
      kind: started.kind,
      photo_count: started.photoCount,
      group_count: groupCount,
      duration_ms: Math.max(0, now - started.startedAt),
    },
    started.room,
  );
};

export const trackOrganizeAnalysisFailed = (
  started: StartedOrganize,
  failureCode: string | undefined,
): void => {
  track(
    "Photo Organize Failed",
    { reason: failureCode ?? "unknown", stage: "analysis" },
    started.room,
  );
};

export const trackOrganizeApplied = (kind: "PERSON" | "SIMILAR", folderCount: number): void => {
  track("Photo Organize Applied", { kind, folder_count: folderCount });
};
