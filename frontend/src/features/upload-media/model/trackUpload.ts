import { type AnalyticsRoomContext, track } from "@/shared/lib";
import type { RejectedSelection, SelectionRejectionCode } from "./selectMediaFiles";
import type { UploadResult } from "./types";

const UPLOAD_FORMATS = ["jpeg", "png", "gif", "webp", "avif", "mp4", "webm", "mov"];

/** 서버의 포맷 판별과 같이 확장자를 우선한다. 파일명 자체는 이벤트로 보내지 않는다. */
const formatOf = (fileName: string, mimeType: string) => {
  const extension = fileName
    ?.match(/\.([^.]+)$/)?.[1]
    .trim()
    .toLowerCase();
  const format = extension === "jpg" ? "jpeg" : extension;
  if (format && UPLOAD_FORMATS.includes(format)) return format;

  const mimeFormat = mimeType?.split(";")[0].trim().toLowerCase();
  const byMime: Record<string, string> = {
    "image/jpeg": "jpeg",
    "image/png": "png",
    "image/gif": "gif",
    "image/webp": "webp",
    "image/avif": "avif",
    "video/mp4": "mp4",
    "video/webm": "webm",
    "video/quicktime": "mov",
  };
  return byMime[mimeFormat] ?? "unknown";
};

/** 전송 전에 걸러진 파일도 실패에 포함한다. 전송 결과에는 이 파일들이 들어가지 않는다. */
export const trackUploadSelectionRejected = (rejected: RejectedSelection[]) => {
  const counts = new Map<SelectionRejectionCode, number>();
  rejected.forEach(({ code }) => counts.set(code, (counts.get(code) ?? 0) + 1));

  counts.forEach((failedCount, reason) => {
    track("Photo Upload Failed", {
      reason,
      failed_count: failedCount,
      stage: "selection",
    });
  });
};

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
    const formatCounts: Record<string, number> = { webp: 0, avif: 0 };
    result.registered.forEach(({ fileName, mimeType }) => {
      const format = formatOf(fileName, mimeType);
      formatCounts[format] = (formatCounts[format] ?? 0) + 1;
    });
    if (result.alreadyRegistered > 0) {
      formatCounts.unknown = (formatCounts.unknown ?? 0) + result.alreadyRegistered;
    }

    track(
      "Photo Uploaded",
      {
        photo_count: uploadedCount,
        failed_count: failedCount,
        duration_ms: Math.round(durationMs),
        uploaded_format_counts: formatCounts,
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
