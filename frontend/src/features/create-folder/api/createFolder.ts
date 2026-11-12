import { RoomFolderSchema } from "../../../entities/room/model/types";
import type { RoomFolder } from "@/entities/room";
import { apiClient } from "@/shared/api";

interface CreateFolderParams {
  roomId: number;
  accessToken: string;
  name: string;
}

export const createFolder = ({
  roomId,
  accessToken,
  name,
}: CreateFolderParams): Promise<RoomFolder> =>
  apiClient<RoomFolder>(`/rooms/${roomId}/folders`, {
    schema: RoomFolderSchema,
    method: "POST",
    token: accessToken,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name }),
  });
