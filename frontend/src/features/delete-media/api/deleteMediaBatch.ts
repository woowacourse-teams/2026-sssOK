import { z } from "zod";
export const DeleteMediaBatchResponseSchema = z.object({
  deletedCount: z.number(),
  deletedMediaIds: z.array(z.number()),
  notFoundMediaIds: z.array(z.number()),
});
import { selectedMediaBody } from "@/entities/media";
import { apiClient } from "@/shared/api";

export type DeleteMediaBatchResponse = z.infer<typeof DeleteMediaBatchResponseSchema>;

export const deleteMediaBatch = ({
  roomId,
  mediaIds,
  token,
}: {
  roomId: number;
  mediaIds: number[];
  token: string;
}): Promise<DeleteMediaBatchResponse> =>
  apiClient<DeleteMediaBatchResponse>(`/rooms/${roomId}/media`, {
    schema: DeleteMediaBatchResponseSchema,
    method: "DELETE",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(selectedMediaBody(mediaIds)),
  });
