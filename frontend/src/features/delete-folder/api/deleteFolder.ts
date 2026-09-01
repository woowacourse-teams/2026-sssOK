import { apiClient } from "@/shared/api";

interface DeleteFolderParams {
  roomId: number;
  folderId: number;
  accessToken: string;
}

export interface DeleteFolderResponse {
  deletedFolderId: number;
  detachedPhotoCount: number;
}

export const deleteFolder = ({ roomId, folderId, accessToken }: DeleteFolderParams) =>
  apiClient<DeleteFolderResponse>(`/rooms/${roomId}/folders/${folderId}`, {
    method: "DELETE",
    token: accessToken,
  });
