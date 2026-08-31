import type { Room } from "@/entities/room";

export interface UpdateRoomRequest {
  name?: string;
  uploadPolicy?: "everyone" | "host";
  expiryHours?: 24 | 72;
}

export type UpdateRoomResponse = Room;
