import { apiClient } from "@/shared/api";
import type { MediaUploaderFilter } from "@/entities/media";
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
  uploader,
  token,
}: {
  roomId: number;
  selection: MediaSelectionRequest;
  folderId: number;
  uploader?: MediaUploaderFilter;
  token: string;
}) =>
  apiClient<AddMediaToFolderResponse>(`/rooms/${roomId}/media/folders`, {
    method: "PUT",
    token,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ selection, folderId, uploader }),
  });
