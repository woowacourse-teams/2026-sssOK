import { apiClient } from "@/shared/api";

interface LeaveRoomParams {
  roomId: number;
  accessToken?: string;
}

export const leaveRoom = ({ roomId, accessToken }: LeaveRoomParams) =>
  apiClient<void>(`/rooms/${roomId}/members/me`, {
    method: "DELETE",
    token: accessToken,
    responseType: "empty",
    errorTracking: {
      level: "error",
      operation: "room_list.leave_room",
      route: "/rooms/:roomId/members/me",
    },
  });
