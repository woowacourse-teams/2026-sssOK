import { z } from "zod";
export const RemoveMediaFromFolderResponseSchema = z.object({
  updatedCount: z.number(),
  movedToRootMediaIds: z.array(z.number()),
  notFoundMediaIds: z.array(z.number()),
  folders: z.array(z.object({ id: z.number(), name: z.string(), photoCount: z.number() })),
});
import { selectedMediaBody } from "@/entities/media";
import { apiClient } from "@/shared/api";

type RemoveMediaFromFolderResponse = z.infer<typeof RemoveMediaFromFolderResponseSchema>;

export const removeMediaFromFolder = ({
  roomId,
  mediaIds,
  folderId,
  token,
}: {
  roomId: number;
  mediaIds: number[];
  folderId: number;
  token: string;
}): Promise<RemoveMediaFromFolderResponse> =>
  apiClient<RemoveMediaFromFolderResponse>(`/rooms/${roomId}/media/folders`, {
    schema: RemoveMediaFromFolderResponseSchema,
    method: "DELETE",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...selectedMediaBody(mediaIds), folderIds: [folderId] }),
  });
