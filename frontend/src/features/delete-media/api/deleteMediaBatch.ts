import { selectedMediaBody } from "@/entities/media";
import { apiClient } from "@/shared/api";

export interface DeleteMediaBatchResponse {
  deletedCount: number;
  deletedMediaIds: number[];
  notFoundMediaIds: number[];
}

export const deleteMediaBatch = ({
  roomId,
  mediaIds,
  token,
}: {
  roomId: number;
  mediaIds: number[];
  token: string;
}) =>
  apiClient<DeleteMediaBatchResponse>(`/rooms/${roomId}/media`, {
    method: "DELETE",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(selectedMediaBody(mediaIds)),
  });
