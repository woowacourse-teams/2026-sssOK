import { http, HttpResponse } from "msw";

import type { MyRoom } from "@/entities/room";
import { API_BASE_URL } from "@/shared/config";
import { thumbnailUrlOf } from "../db";
import { MOCK_ROOM_CODES } from "./room";

const INITIAL_MY_ROOMS: MyRoom[] = [
  {
    roomId: 5031,
    code: MOCK_ROOM_CODES.active,
    name: "제주 졸업여행",
    isHost: true,
    mediaCount: 128,
    memberCount: 14,
    thumbnailUrl: thumbnailUrlOf(1),
  },
  {
    roomId: 5032,
    code: MOCK_ROOM_CODES.second,
    name: "동아리 MT",
    isHost: false,
    mediaCount: 64,
    memberCount: 9,
    thumbnailUrl: thumbnailUrlOf(2),
  },
  {
    roomId: 5035,
    code: MOCK_ROOM_CODES.hostOnly,
    name: "성수 카페 투어",
    isHost: false,
    mediaCount: 0,
    memberCount: 4,
    thumbnailUrl: null,
  },
];

let leftRoomIds = new Set<number>();

export const resetMyRooms = () => {
  leftRoomIds = new Set();
};

const myRooms = () => INITIAL_MY_ROOMS.filter((room) => !leftRoomIds.has(room.roomId));

export const myRoomsHandlers = [
  http.get(`${API_BASE_URL}/members/me/rooms`, () =>
    HttpResponse.json({ data: { rooms: myRooms() } }),
  ),

  http.delete(`${API_BASE_URL}/rooms/:roomId/members/me`, ({ params }) => {
    const roomId = Number(params.roomId);
    const room = INITIAL_MY_ROOMS.find((candidate) => candidate.roomId === roomId);

    if (!room) {
      return HttpResponse.json(
        { code: "ROOM_NOT_FOUND", message: "방을 찾을 수 없습니다." },
        { status: 404 },
      );
    }

    if (room.isHost) {
      return HttpResponse.json(
        { code: "ROOM_HOST_CANNOT_LEAVE", message: "방장은 방에서 나갈 수 없습니다." },
        { status: 409 },
      );
    }

    leftRoomIds.add(roomId);
    return new HttpResponse(null, { status: 204 });
  }),
];
