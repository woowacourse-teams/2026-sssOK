import { apiClient } from "@/shared/api";
import type { IssueUploadUrlsRequest, IssueUploadUrlsResponse } from "./types";

/**
 * 서명 URL 발급. **고른 파일 전체를 한 번에** 보낸다 — 파일마다 부르면 안 된다.
 *
 * 파일 단위 실패는 예외로 던져지지 않고 응답의 `rejected` 로 갈라져 온다.
 * 방·권한 문제(403·410 등)만 `ApiError` 로 던져진다.
 */
export const issueUploadUrls = (roomId: number, request: IssueUploadUrlsRequest, token: string) => {
  return apiClient<IssueUploadUrlsResponse>(`/rooms/${roomId}/media/upload-urls`, {
    method: "POST",
    token,
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
};
