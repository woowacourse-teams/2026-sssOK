import { z } from "zod";
export const DeleteFolderResponseSchema = z.object({
  deletedFolderId: z.number(),
  detachedPhotoCount: z.number(),
});
import { apiClient } from "@/shared/api";

interface DeleteFolderParams {
  roomId: number;
  folderId: number;
  accessToken: string;
}

export type DeleteFolderResponse = z.infer<typeof DeleteFolderResponseSchema>;

export const deleteFolder = ({
  roomId,
  folderId,
  accessToken,
}: DeleteFolderParams): Promise<DeleteFolderResponse> =>
  apiClient<DeleteFolderResponse>(`/rooms/${roomId}/folders/${folderId}`, {
    schema: DeleteFolderResponseSchema,
    method: "DELETE",
    token: accessToken,
  });
