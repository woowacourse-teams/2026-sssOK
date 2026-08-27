import { apiClient } from "@/shared/api";
import type { DownloadJob } from "./types";

interface CreateDownloadJobParams {
  roomId: number;
  token: string;
  /** 고른 미디어. `folderId` 와 함께 보내면 400 이다. */
  mediaIds?: number[];
  folderId?: number;
}

/**
 * 압축 잡을 만든다 (B-7-1). **압축을 기다리지 않는다** — 202 와 함께 잡 번호만 온다.
 * 실제 zip 은 `getDownloadJobProgress` 를 폴링해 `READY` 가 된 뒤에야 받을 수 있다.
 *
 * 둘 다 생략하면 방 전체가 대상이다.
 */
export const createDownloadJob = ({ roomId, token, mediaIds, folderId }: CreateDownloadJobParams) =>
  apiClient<DownloadJob>(`/rooms/${roomId}/downloads`, {
    method: "POST",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ mediaIds, folderId }),
  });
