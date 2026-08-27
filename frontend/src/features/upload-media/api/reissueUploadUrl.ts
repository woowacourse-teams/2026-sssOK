import { apiClient } from "@/shared/api";
import type { ReissueUploadUrlRequest, ReissuedUpload } from "./types";

/**
 * 재발급. 만료되거나 전송이 깨진 업로드에 새 URL 을 준다.
 *
 * `mediaId` 는 그대로 두고 스토리지 키만 갈아끼운다 — 뒤늦게 도착한 옛 PUT 이
 * 새 파일을 덮지 못한다. 서버 한도(5회)를 넘기면 429 `UPLOAD_RETRY_EXCEEDED` 로 던져진다.
 */
export const reissueUploadUrl = (
  roomId: number,
  mediaId: number,
  request: ReissueUploadUrlRequest,
  token: string,
) => {
  return apiClient<ReissuedUpload>(`/rooms/${roomId}/media/${mediaId}/upload-url`, {
    method: "POST",
    token,
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
};
