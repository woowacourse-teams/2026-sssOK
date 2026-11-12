import { z } from "zod";
export const AddMediaToFolderResponseSchema = z.object({
  updatedCount: z.number(),
  alreadyInCount: z.number(),
  notFoundMediaIds: z.array(z.number()),
  folder: z.object({ id: z.number(), name: z.string(), photoCount: z.number() }),
});
import { selectedMediaBody } from "@/entities/media";
import { apiClient } from "@/shared/api";

type AddMediaToFolderResponse = z.infer<typeof AddMediaToFolderResponseSchema>;

export const addMediaToFolder = ({
  roomId,
  mediaIds,
  folderId,
  token,
}: {
  roomId: number;
  mediaIds: number[];
  folderId: number;
  token: string;
}): Promise<AddMediaToFolderResponse> =>
  apiClient<AddMediaToFolderResponse>(`/rooms/${roomId}/media/folders`, {
    schema: AddMediaToFolderResponseSchema,
    method: "PUT",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...selectedMediaBody(mediaIds), folderId }),
  });
