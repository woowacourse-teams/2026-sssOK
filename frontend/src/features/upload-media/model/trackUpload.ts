import { type AnalyticsRoomContext, track } from "@/shared/lib";
import type { UploadResult } from "./types";

/**
 * 올리기 한 판의 결말을 이벤트로 남긴다.
 *
 * 한 장이라도 올라갔으면 활성 행동이다 — 실패한 장수는 `failed_count` 로 함께 남긴다.
 * 한 장도 못 올렸을 때만 실패로 센다. 발급 단계에서 거절된 파일도 못 올린 것이라 실패에 넣는다.
 */
export const trackUpload = (
  result: UploadResult,
  durationMs: number,
  room: AnalyticsRoomContext | null,
) => {
  const uploadedCount = result.registered.length + result.alreadyRegistered;
  const failedCount = result.failed.length + result.rejected.length;

  if (uploadedCount > 0) {
    track(
      "Photo Uploaded",
      {
        photo_count: uploadedCount,
        failed_count: failedCount,
        duration_ms: Math.round(durationMs),
      },
      room,
    );
    return;
  }

  if (failedCount > 0) {
    track(
      "Photo Upload Failed",
      { reason: result.failed[0]?.code ?? "REJECTED", failed_count: failedCount },
      room,
    );
  }
};

/** 결말을 만들지도 못하고 판 전체가 무너진 경우 (방·권한 문제 등) */
export const trackUploadError = (fileCount: number, room: AnalyticsRoomContext | null) => {
  track("Photo Upload Failed", { reason: "REQUEST_FAILED", failed_count: fileCount }, room);
};
