import { RoomSchema } from "../model/types";
import { apiClient } from "@/shared/api";
import type { Room } from "../model/types";

export const getRoom = (code: string, token?: string): Promise<Room> =>
  apiClient<Room>(`/rooms/${code}`, {
    schema: RoomSchema,
    token,
    errorTracking: {
      level: "fatal",
      operation: "room_entry.get_room",
      route: "/rooms/:code",
    },
  });
