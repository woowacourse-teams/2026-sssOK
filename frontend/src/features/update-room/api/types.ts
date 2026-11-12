import { z } from "zod";
import { RoomSchema } from "../../../entities/room/model/types";
export const UpdateRoomResponseSchema = RoomSchema;

export interface UpdateRoomRequest {
  name?: string;
  uploadPolicy?: "everyone" | "host";
}

export type UpdateRoomResponse = z.infer<typeof UpdateRoomResponseSchema>;
