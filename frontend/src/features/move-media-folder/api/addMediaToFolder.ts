import { apiClient } from "@/shared/api";

interface AddMediaToFolderResponse {
  updatedCount: number;
  alreadyInCount: number;
  notFoundMediaIds: number[];
  folder: {
    id: number;
    name: string;
    photoCount: number;
  };
}

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
}) =>
  apiClient<AddMediaToFolderResponse>(`/rooms/${roomId}/media/folders`, {
    method: "PUT",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ mediaIds, folderId }),
  });
