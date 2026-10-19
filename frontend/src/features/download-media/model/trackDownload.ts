import { type AnalyticsDownloadSource, type AnalyticsRoomContext, track } from "@/shared/lib";
import type { DownloadMode, DownloadOutcome, DownloadTarget } from "./types";
import type { ShareBatchResult } from "./useMediaDownload";

interface DownloadRun {
  mode: DownloadMode;
  source: AnalyticsDownloadSource;
  /** 이번 판에 고른 장수 */
  selectedCount: number;
  /** 방 전체 장수 (영상 포함) */
  roomPhotoCount: number;
  /**
   * "전체 선택" 으로 고른 판인지. 골라 받기(H5)를 보는 값이다.
   * 장수로 가려내지 않는다 — 방 장수에는 갤러리에 안 뜨는 영상이 섞이고,
   * 전체 선택은 불러온 사진까지만 고르기 때문이다.
   */
  isSelectAll: boolean;
  /**
   * 받기를 시작해서 다 받기까지 걸린 시간. 사진첩으로 넘기는 판은 받아둔 뒤 사용자가
   * 묶음마다 탭하는 시간이 끼므로, 그 시간은 빼고 다 받은 시점까지만 잰다.
   */
  durationMs: number;
  /** 받기를 시작한 방. 끝나기 전에 갤러리를 떠나도 그 방으로 남긴다. */
  room: AnalyticsRoomContext | null;
}

/** 받기 한 판을 시작했을 때. 끝난 판과 견줘 완료율과 도중에 그만둔 비율을 본다. */
export const trackDownloadStarted = (
  targets: DownloadTarget[],
  {
    mode,
    source,
    room,
  }: { mode: DownloadMode; source: AnalyticsDownloadSource; room: AnalyticsRoomContext | null },
) =>
  track(
    "Photo Download Started",
    {
      mode,
      source,
      photo_count: targets.length,
      total_bytes: targets.reduce((sum, target) => sum + target.size, 0),
    },
    room,
  );

/**
 * 받기 한 판의 결말을 이벤트로 남긴다.
 *
 * 공유 시트로 넘길 준비만 된 판(`readyToShare`)은 세지 않는다 — 사용자가 시트를 열어
 * 넣으면 `saved` 로 한 번 더 끝나서, 여기서도 세면 같은 판이 두 번 잡힌다.
 * 취소·닫기는 받은 것도 실패한 것도 아니라 남기지 않는다.
 */
export const trackDownload = (outcome: DownloadOutcome, run: DownloadRun) => {
  const durationMs = Math.round(run.durationMs);

  switch (outcome.type) {
    case "saved":
      track(
        "Photo Downloaded",
        {
          mode: run.mode,
          source: run.source,
          photo_count: outcome.savedCount,
          failed_count: outcome.failed.length,
          total_count: run.roomPhotoCount,
          is_select_all: run.isSelectAll,
          duration_ms: durationMs,
        },
        run.room,
      );
      return;
    case "empty":
      track(
        "Photo Download Failed",
        {
          mode: run.mode,
          source: run.source,
          reason: "all_failed",
          failed_count: outcome.failed.length,
          duration_ms: durationMs,
        },
        run.room,
      );
      return;
    case "failed":
      track(
        "Photo Download Failed",
        {
          mode: run.mode,
          source: run.source,
          reason: outcome.reason,
          failed_count: run.selectedCount,
          duration_ms: durationMs,
        },
        run.room,
      );
      return;
    default:
      return;
  }
};

/**
 * 묶음 하나를 공유 시트로 넘긴 결과를 남긴다.
 */
export const trackShareBatch = (batch: ShareBatchResult, room: AnalyticsRoomContext | null) =>
  track(
    "Photo Share Batch Settled",
    {
      batch_index: batch.batchIndex,
      batch_count: batch.batchCount,
      photo_count: batch.photoCount,
      total_bytes: batch.totalBytes,
      result: batch.result,
    },
    room,
  );
