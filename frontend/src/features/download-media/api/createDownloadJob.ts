import { selectedMediaBody } from "@/entities/media";
import { apiClient } from "@/shared/api";
import type { DownloadJob } from "./types";

interface CreateDownloadJobParams {
  roomId: number;
  token: string;
  /** 고른 미디어. */
  mediaIds: number[];
}

/**
 * 압축 잡을 만든다 (B-7-1). **압축을 기다리지 않는다** — 202 와 함께 잡 번호만 온다.
 * 실제 zip 은 `getDownloadJobProgress` 를 폴링해 `READY` 가 된 뒤에야 받을 수 있다.
 */
export const createDownloadJob = ({ roomId, token, mediaIds }: CreateDownloadJobParams) =>
  apiClient<DownloadJob>(`/rooms/${roomId}/downloads/zip`, {
    method: "POST",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(selectedMediaBody(mediaIds)),
  });
