import type { RoomFolder } from "@/entities/room";
import { apiClient } from "@/shared/api";

interface CreateFolderParams {
  roomId: number;
  accessToken: string;
  name: string;
}

export const createFolder = ({ roomId, accessToken, name }: CreateFolderParams) =>
  apiClient<RoomFolder>(`/rooms/${roomId}/folders`, {
    method: "POST",
    token: accessToken,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
