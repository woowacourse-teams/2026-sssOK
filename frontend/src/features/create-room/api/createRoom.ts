import { apiClient } from "@/shared/api";
import type { CreateRoomRequest, CreateRoomResponse } from "./types";

export const createRoom = (request: CreateRoomRequest, token: string) => {
  return apiClient<CreateRoomResponse>("/rooms", {
    method: "POST",
    token,
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
};
