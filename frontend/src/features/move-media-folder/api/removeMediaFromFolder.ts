import { apiClient } from "@/shared/api";

interface RemoveMediaFromFolderResponse {
  updatedCount: number;
  movedToRootMediaIds: number[];
  notFoundMediaIds: number[];
  folders: {
    id: number;
    name: string;
    photoCount: number;
  }[];
}

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
}) =>
  apiClient<RemoveMediaFromFolderResponse>(`/rooms/${roomId}/media/folders`, {
    method: "DELETE",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ mediaIds, folderIds: [folderId] }),
  });
