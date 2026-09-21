import { apiClient } from "@/shared/api";
import type { MediaSelectionRequest } from "@/features/select-media";

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
  selection,
  folderId,
  token,
}: {
  roomId: number;
  selection: MediaSelectionRequest;
  folderId: number;
  token: string;
}) =>
  apiClient<AddMediaToFolderResponse>(`/rooms/${roomId}/media/folders`, {
    method: "PUT",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ selection, folderId }),
  });
