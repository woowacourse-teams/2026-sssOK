import { apiClient } from "@/shared/api";
import type { MediaSelectionRequest } from "@/features/select-media";

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
  selection,
  folderId,
  token,
}: {
  roomId: number;
  selection: MediaSelectionRequest;
  folderId: number;
  token: string;
}) =>
  apiClient<RemoveMediaFromFolderResponse>(`/rooms/${roomId}/media/folders`, {
    method: "DELETE",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ selection, folderIds: [folderId] }),
  });
