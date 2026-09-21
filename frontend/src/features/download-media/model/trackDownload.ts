import { track } from "@/shared/lib";
import type { DownloadMode, DownloadOutcome } from "./types";

interface DownloadRun {
  mode: DownloadMode;
  /** 이번 판에 고른 장수 */
  selectedCount: number;
  /** 방 전체 장수. 고른 장수와 견줘 골라 받기(H5)를 본다. */
  roomPhotoCount: number;
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
      track("Photo Downloaded", {
        mode: run.mode,
        photo_count: outcome.savedCount,
        failed_count: outcome.failed.length,
        total_count: run.roomPhotoCount,
        is_select_all: run.selectedCount >= run.roomPhotoCount,
      });
      return;
    case "empty":
      track("Photo Download Failed", {
        mode: run.mode,
        reason: "all_failed",
        failed_count: outcome.failed.length,
      });
      return;
    case "failed":
      track("Photo Download Failed", {
        mode: run.mode,
        reason: outcome.reason,
        failed_count: run.selectedCount,
      });
      return;
    default:
      return;
  }
};
