import type { RoomFolder } from "@/entities/room";
import { apiClient } from "@/shared/api";

interface EditFolderParams {
  roomId: number;
  folderId: number;
  accessToken: string;
  name: string;
}

export const editFolder = ({ roomId, folderId, accessToken, name }: EditFolderParams) =>
  apiClient<RoomFolder>(`/rooms/${roomId}/folders/${folderId}`, {
    method: "PATCH",
    token: accessToken,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
