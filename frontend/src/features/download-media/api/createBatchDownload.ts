import { apiClient } from "@/shared/api";
import type { BatchDownload } from "./types";

interface CreateBatchDownloadParams {
  roomId: number;
  token: string;
  /** 고른 미디어. `folderId` 와 함께 보내면 400 이다. */
  mediaIds?: number[];
  folderId?: number;
}

/**
 * 고른 미디어의 서명 다운로드 URL 을 **한 번에** 받는다 (B-6). 압축하지 않는다.
 *
 * 장마다 단건 다운로드를 부르지 않는 이유가 있다. 단건은 302 로 스토리지를 가리키는데,
 * 브라우저가 그 리다이렉트를 따라갈 때 `Authorization` 을 스토리지까지 들고 간다.
 * 서명은 `host` 만 덮고 있어 스토리지가 400 으로 끊고, CORS 응답 헤더가 없어져
 * 브라우저에서는 통째로 막힌다. 여기서 URL 만 먼저 받아오면 그 왕복 자체가 없다.
 *
 * 아직 처리 중인 미디어는 서버가 대상에서 뺀다 — 응답 `files` 가 고른 장수보다 적을 수 있다.
 * 둘 다 생략하면 방 전체가 대상이다.
 */
export const createBatchDownload = ({
  roomId,
  token,
  mediaIds,
  folderId,
}: CreateBatchDownloadParams) =>
  apiClient<BatchDownload>(`/rooms/${roomId}/downloads/batch`, {
    method: "POST",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ mediaIds, folderId }),
  });
