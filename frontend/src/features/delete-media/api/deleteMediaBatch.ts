import { apiClient } from "@/shared/api";
import type { MediaSelectionRequest } from "@/features/select-media";

export interface DeleteMediaBatchResponse {
  deleted: number[];
  skipped: { mediaId: number; code: string; message: string }[];
  deletedCount: number;
}

/** 다중 삭제 UI를 붙일 때 사용할 API. 일부 실패도 성공 응답의 skipped로 내려온다. */
export const deleteMediaBatch = ({
  roomId,
  selection,
  mediaIds,
  token,
}: {
  roomId: number;
  selection?: MediaSelectionRequest;
  /** @deprecated selection을 사용한다. */
  mediaIds?: number[];
  token: string;
}) =>
  apiClient<DeleteMediaBatchResponse>(`/rooms/${roomId}/media`, {
    method: "DELETE",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      selection: selection ?? { mode: "include", ids: mediaIds ?? [] },
    }),
  });
