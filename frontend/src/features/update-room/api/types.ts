import type { Room } from "@/entities/room";

export interface UpdateRoomRequest {
  name?: string;
  uploadPolicy?: "everyone" | "host";
}

export type UpdateRoomResponse = Room;
