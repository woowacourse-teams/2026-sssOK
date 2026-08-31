import { apiClient } from "@/shared/api";
import type { UpdateRoomRequest, UpdateRoomResponse } from "./types";

interface UpdateRoomParams {
  roomId: number;
  accessToken: string;
  request: UpdateRoomRequest;
}

export const updateRoom = ({ roomId, accessToken, request }: UpdateRoomParams) =>
  apiClient<UpdateRoomResponse>(`/rooms/${roomId}`, {
    method: "PATCH",
    token: accessToken,
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
