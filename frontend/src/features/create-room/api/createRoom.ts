import { CreateRoomResponseSchema } from "./types";
import { apiClient } from "@/shared/api";
import type { CreateRoomRequest, CreateRoomResponse } from "./types";

export const createRoom = (
  request: CreateRoomRequest,
  token: string,
): Promise<CreateRoomResponse> => {
  return apiClient<CreateRoomResponse>("/rooms", {
    schema: CreateRoomResponseSchema,
    method: "POST",
    token,
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
};
