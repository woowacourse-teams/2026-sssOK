import { apiClient } from "@/shared/api";
import type { DownloadJobProgress } from "./types";

interface GetDownloadJobProgressParams {
  roomId: number;
  jobId: string;
  token: string;
}

/**
 * 압축 잡의 상태를 한 번 조회한다 (B-7-2).
 *
 * 반복은 여기서 하지 않는다 — 언제 멈출지는 부르는 쪽(`pollDownloadJob`)이 정한다.
 * `putToStorage` 와 같은 규칙이다: 이 안에 반복문을 두지 않는다.
 */
export const getDownloadJobProgress = ({ roomId, jobId, token }: GetDownloadJobProgressParams) =>
  apiClient<DownloadJobProgress>(`/rooms/${roomId}/downloads/zip/${jobId}`, { token });
