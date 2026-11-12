import { z } from "zod";
import { MyRoomSchema } from "../model/types";
export const MyRoomsResponseSchema = z.object({ rooms: z.array(MyRoomSchema) });
import { apiClient } from "@/shared/api";
import type { MyRoom } from "../model/types";

type MyRoomsResponse = z.infer<typeof MyRoomsResponseSchema>;

export const getMyRooms = async (token?: string): Promise<MyRoom[]> => {
  const { rooms } = await apiClient<MyRoomsResponse>("/members/me/rooms", {
    schema: MyRoomsResponseSchema,
    token,
    errorTracking: {
      level: "error",
      operation: "room_list.get_my_rooms",
      route: "/members/me/rooms",
    },
  });

  return rooms;
};
