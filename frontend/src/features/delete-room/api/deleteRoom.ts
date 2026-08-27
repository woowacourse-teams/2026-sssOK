import { apiClient } from "@/shared/api";

interface DeleteRoomParams {
  roomId: number;
  accessToken: string;
}

export interface DeleteRoomResponse {
  code: string;
  status: "DELETED";
  deletedAt: string;
  purgeAt: string;
}

export const deleteRoom = ({ roomId, accessToken }: DeleteRoomParams) =>
  apiClient<DeleteRoomResponse>(`/rooms/${roomId}`, {
    method: "DELETE",
    token: accessToken,
  });
