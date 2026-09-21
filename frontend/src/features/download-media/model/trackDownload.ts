import { type AnalyticsDownloadSource, type AnalyticsRoomContext, track } from "@/shared/lib";
import type { DownloadMode, DownloadOutcome } from "./types";

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
  /** 받기를 시작한 방. 끝나기 전에 갤러리를 떠나도 그 방으로 남긴다. */
  room: AnalyticsRoomContext | null;
}

/**
 * 받기 한 판의 결말을 이벤트로 남긴다.
 *
 * 공유 시트로 넘길 준비만 된 판(`readyToShare`)은 세지 않는다 — 사용자가 시트를 열어
 * 넣으면 `saved` 로 한 번 더 끝나서, 여기서도 세면 같은 판이 두 번 잡힌다.
 * 취소·닫기는 받은 것도 실패한 것도 아니라 남기지 않는다.
 */
export const trackDownload = (outcome: DownloadOutcome, run: DownloadRun) => {
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
        },
        run.room,
      );
      return;
    default:
      return;
  }
};
