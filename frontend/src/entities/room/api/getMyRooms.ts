import { apiClient } from "@/shared/api";
import type { MyRoom } from "../model/types";

interface MyRoomsResponse {
  rooms: MyRoom[];
}

export const getMyRooms = async (token?: string) => {
  const { rooms } = await apiClient<MyRoomsResponse>("/members/me/rooms", {
    token,
    errorTracking: {
      level: "error",
      operation: "room_list.get_my_rooms",
      route: "/members/me/rooms",
    },
  });

  return rooms;
};
