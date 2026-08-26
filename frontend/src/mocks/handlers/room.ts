import { http, HttpResponse } from "msw";

import { API_PREFIX } from "../config";

const ROOM_CODE_PATTERN = /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/;
const ACTIVE_ROOM_ID = 5031;
const MOCK_NOW = new Date("2026-08-18T06:00:00Z");
const ACTIVE_ROOM_REMAINING_TIME = (23 * 60 + 10) * 60 * 1000;

export const MOCK_ROOM_CODES = {
  active: "7K93QX2S",
  expired: "EXPRED77",
  deleted: "DELETED7",
  notFound: "NTFUND23",
  second: "QRST6789",
  invalid: "NOTFOUND",
} as const;

type RoomStatus = "ACTIVE" | "EXPIRED" | "DELETED";

interface MockRoom {
  roomId: number;
  code: string;
  name: string;
  status: RoomStatus;
  hostId: number;
  hostName: string;
  createdAt: string;
  expiresAt: string;
  uploadPolicy: "everyone" | "host";
  photoCount: number;
  folders: Array<{
    id: number;
    name: string;
    createdAt: string;
    photoCount: number;
  }>;
}

interface MockMedia {
  mediaId: number;
  type: "IMAGE" | "VIDEO";
  fileName: string;
  mimeType: string;
  size: number;
  thumbnailUrl: string;
  originalUrl: string;
  width: number;
  height: number;
  duration: number | null;
  folderIds: number[];
  uploaderId: number;
  uploaderName: string;
  status: "READY";
  uploadedAt: string;
}

const createActiveRoom = (): MockRoom => ({
  roomId: ACTIVE_ROOM_ID,
  code: MOCK_ROOM_CODES.active,
  name: "제주 여행",
  status: "ACTIVE",
  hostId: 10234,
  hostName: "민수",
  createdAt: "2026-08-18T05:30:00Z",
  expiresAt: new Date(Date.now() + ACTIVE_ROOM_REMAINING_TIME).toISOString(),
  uploadPolicy: "everyone",
  photoCount: 13,
  folders: [
    {
      id: 501,
      name: "첫째 날",
      createdAt: "2026-08-18T06:10:00Z",
      photoCount: 4,
    },
  ],
});

let activeRoom = createActiveRoom();

const room = (code: string, status: RoomStatus, joined = false) => ({
  ...activeRoom,
  code,
  status,
  joined,
  expiresAt: status === "ACTIVE" ? activeRoom.expiresAt : "2026-08-01T05:30:00Z",
});

const mediaItems: MockMedia[] = [
  {
    mediaId: 5012,
    type: "IMAGE",
    fileName: "IMG_0421.jpg",
    mimeType: "image/jpeg",
    size: 3840219,
    thumbnailUrl: "https://cdn.example.com/rooms/1024/5012_thumb.webp",
    originalUrl: "https://cdn.example.com/rooms/1024/5012.jpg",
    width: 4032,
    height: 3024,
    duration: null,
    folderIds: [501],
    uploaderId: 10234,
    uploaderName: "로지",
    status: "READY",
    uploadedAt: "2026-08-18T20:15:00+09:00",
  },
  {
    mediaId: 5011,
    type: "VIDEO",
    fileName: "VID_0032.mp4",
    mimeType: "video/mp4",
    size: 734003200,
    thumbnailUrl: "https://cdn.example.com/rooms/1024/5011_thumb.webp",
    originalUrl: "https://cdn.example.com/rooms/1024/5011.mp4",
    width: 1920,
    height: 1080,
    duration: 34,
    folderIds: [],
    uploaderId: 7,
    uploaderName: "미미",
    status: "READY",
    uploadedAt: "2026-08-18T20:10:00+09:00",
  },
  {
    mediaId: 5010,
    type: "IMAGE",
    fileName: "IMG_0419.jpg",
    mimeType: "image/jpeg",
    size: 2912048,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5010/600/700",
    originalUrl: "https://picsum.photos/seed/sssok-5010/1200/1400",
    width: 3024,
    height: 4032,
    duration: null,
    folderIds: [501],
    uploaderId: 10234,
    uploaderName: "민수",
    status: "READY",
    uploadedAt: "2026-08-18T20:05:00+09:00",
  },
  {
    mediaId: 5009,
    type: "IMAGE",
    fileName: "IMG_0418.jpg",
    mimeType: "image/jpeg",
    size: 3419231,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5009/600/700",
    originalUrl: "https://picsum.photos/seed/sssok-5009/1200/1400",
    width: 4032,
    height: 3024,
    duration: null,
    folderIds: [501],
    uploaderId: 21,
    uploaderName: "포키",
    status: "READY",
    uploadedAt: "2026-08-18T19:58:00+09:00",
  },
  {
    mediaId: 5008,
    type: "VIDEO",
    fileName: "VID_0031.mp4",
    mimeType: "video/mp4",
    size: 182452224,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5008/600/700",
    originalUrl: "https://cdn.example.com/rooms/1024/5008.mp4",
    width: 1920,
    height: 1080,
    duration: 58,
    folderIds: [501],
    uploaderId: 7,
    uploaderName: "미미",
    status: "READY",
    uploadedAt: "2026-08-18T19:50:00+09:00",
  },
  {
    mediaId: 5007,
    type: "IMAGE",
    fileName: "IMG_0417.jpg",
    mimeType: "image/jpeg",
    size: 2643981,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5007/600/700",
    originalUrl: "https://picsum.photos/seed/sssok-5007/1200/1400",
    width: 3024,
    height: 4032,
    duration: null,
    folderIds: [],
    uploaderId: 10234,
    uploaderName: "민수",
    status: "READY",
    uploadedAt: "2026-08-18T19:42:00+09:00",
  },
  {
    mediaId: 5006,
    type: "IMAGE",
    fileName: "IMG_0416.jpg",
    mimeType: "image/jpeg",
    size: 3187204,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5006/600/700",
    originalUrl: "https://picsum.photos/seed/sssok-5006/1200/1400",
    width: 4032,
    height: 3024,
    duration: null,
    folderIds: [],
    uploaderId: 12,
    uploaderName: "로지",
    status: "READY",
    uploadedAt: "2026-08-18T19:35:00+09:00",
  },
  {
    mediaId: 5005,
    type: "IMAGE",
    fileName: "IMG_0415.jpg",
    mimeType: "image/jpeg",
    size: 2258176,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5005/600/700",
    originalUrl: "https://picsum.photos/seed/sssok-5005/1200/1400",
    width: 3024,
    height: 4032,
    duration: null,
    folderIds: [],
    uploaderId: 21,
    uploaderName: "포키",
    status: "READY",
    uploadedAt: "2026-08-18T19:27:00+09:00",
  },
  {
    mediaId: 5004,
    type: "VIDEO",
    fileName: "VID_0030.mp4",
    mimeType: "video/mp4",
    size: 246415360,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5004/600/700",
    originalUrl: "https://cdn.example.com/rooms/1024/5004.mp4",
    width: 1080,
    height: 1920,
    duration: 72,
    folderIds: [],
    uploaderId: 10234,
    uploaderName: "민수",
    status: "READY",
    uploadedAt: "2026-08-18T19:20:00+09:00",
  },
  {
    mediaId: 5003,
    type: "IMAGE",
    fileName: "IMG_0414.jpg",
    mimeType: "image/jpeg",
    size: 2839104,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5003/600/700",
    originalUrl: "https://picsum.photos/seed/sssok-5003/1200/1400",
    width: 4032,
    height: 3024,
    duration: null,
    folderIds: [],
    uploaderId: 7,
    uploaderName: "미미",
    status: "READY",
    uploadedAt: "2026-08-18T19:12:00+09:00",
  },
  {
    mediaId: 5002,
    type: "IMAGE",
    fileName: "IMG_0413.jpg",
    mimeType: "image/jpeg",
    size: 3124800,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5002/600/700",
    originalUrl: "https://picsum.photos/seed/sssok-5002/1200/1400",
    width: 3024,
    height: 4032,
    duration: null,
    folderIds: [],
    uploaderId: 10234,
    uploaderName: "민수",
    status: "READY",
    uploadedAt: "2026-08-18T19:04:00+09:00",
  },
  {
    mediaId: 5001,
    type: "IMAGE",
    fileName: "IMG_0412.jpg",
    mimeType: "image/jpeg",
    size: 2490368,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5001/600/700",
    originalUrl: "https://picsum.photos/seed/sssok-5001/1200/1400",
    width: 4032,
    height: 3024,
    duration: null,
    folderIds: [],
    uploaderId: 12,
    uploaderName: "로지",
    status: "READY",
    uploadedAt: "2026-08-18T18:55:00+09:00",
  },
  {
    mediaId: 5000,
    type: "IMAGE",
    fileName: "IMG_0411.jpg",
    mimeType: "image/jpeg",
    size: 2719744,
    thumbnailUrl: "https://picsum.photos/seed/sssok-5000/600/700",
    originalUrl: "https://picsum.photos/seed/sssok-5000/1200/1400",
    width: 3024,
    height: 4032,
    duration: null,
    folderIds: [],
    uploaderId: 21,
    uploaderName: "포키",
    status: "READY",
    uploadedAt: "2026-08-18T18:47:00+09:00",
  },
];

const joinedRooms = new Set<string>();

export const resetRoomHandlers = () => {
  joinedRooms.clear();
  activeRoom = createActiveRoom();
};

const unauthorized = () =>
  HttpResponse.json({ code: "UNAUTHORIZED", message: "인증이 필요합니다." }, { status: 401 });

const roomNotFound = () =>
  HttpResponse.json(
    { code: "ROOM_NOT_FOUND", message: "존재하지 않는 방입니다." },
    { status: 404 },
  );

export const roomHandlers = [
  http.get(`${API_PREFIX}/rooms/:code`, ({ request, params }) => {
    const code = String(params.code);
    const token = request.headers.get("Authorization");
    const joined = token !== null && joinedRooms.has(`${token}:${ACTIVE_ROOM_ID}`);

    if (!ROOM_CODE_PATTERN.test(code)) {
      return HttpResponse.json(
        { code: "INVALID_ROOM_CODE", message: "방 코드 형식이 올바르지 않습니다." },
        { status: 400 },
      );
    }

    if (code === MOCK_ROOM_CODES.expired) {
      return HttpResponse.json({ data: room(code, "EXPIRED") });
    }

    if (code === MOCK_ROOM_CODES.deleted) {
      return HttpResponse.json({ data: room(code, "DELETED") });
    }

    if (code === MOCK_ROOM_CODES.active) {
      return HttpResponse.json({ data: room(code, activeRoom.status, joined) });
    }

    if (code === MOCK_ROOM_CODES.second) {
      return HttpResponse.json({ data: room(code, "ACTIVE", joined) });
    }

    return roomNotFound();
  }),

  http.get(`${API_PREFIX}/rooms/:roomId/media`, ({ request, params }) => {
    if (request.headers.get("Authorization") === null) {
      return unauthorized();
    }

    if (Number(params.roomId) !== ACTIVE_ROOM_ID) {
      return roomNotFound();
    }

    return HttpResponse.json({
      data: {
        items: mediaItems,
      },
    });
  }),

  http.post(`${API_PREFIX}/rooms/:roomId/members`, ({ request, params }) => {
    const token = request.headers.get("Authorization");

    if (token === null) {
      return unauthorized();
    }

    const roomId = Number(params.roomId);
    const alreadyJoined = joinedRooms.has(`${token}:${roomId}`);

    joinedRooms.add(`${token}:${roomId}`);

    return HttpResponse.json(
      {
        data: {
          roomId,
          userId: 10234,
          displayName: "해니",
          hostId: 10234,
          joinedAt: "2026-08-18T05:31:00Z",
        },
      },
      { status: alreadyJoined ? 200 : 201 },
    );
  }),

  http.post(`${API_PREFIX}/rooms`, async ({ request }) => {
    if (request.headers.get("Authorization") === null) {
      return unauthorized();
    }

    const { name, uploadPolicy } = (await request.json()) as {
      name: string;
      uploadPolicy: "everyone" | "host";
      expiryHours: 24 | 72;
    };

    return HttpResponse.json(
      {
        data: {
          roomId: ACTIVE_ROOM_ID,
          code: MOCK_ROOM_CODES.active,
          name,
          hostId: 10234,
          hostName: "민수",
          createdAt: "2026-08-18T05:30:00Z",
          expiresAt: "2026-08-19T05:30:00Z",
          uploadPolicy,
        },
      },
      { status: 201 },
    );
  }),

  http.patch(`${API_PREFIX}/rooms/:roomId`, async ({ request, params }) => {
    if (request.headers.get("Authorization") === null) {
      return unauthorized();
    }

    if (Number(params.roomId) !== ACTIVE_ROOM_ID) {
      return roomNotFound();
    }

    const updates = (await request.json().catch(() => ({}))) as {
      name?: string;
      uploadPolicy?: "everyone" | "host";
      expiryHours?: number;
    };

    if (Object.keys(updates).length === 0) {
      return HttpResponse.json(
        { code: "EMPTY_PATCH", message: "수정할 항목을 하나 이상 보내주세요." },
        { status: 400 },
      );
    }

    const expiresAt =
      updates.expiryHours === undefined
        ? activeRoom.expiresAt
        : new Date(MOCK_NOW.getTime() + updates.expiryHours * 60 * 60 * 1000).toISOString();

    activeRoom = {
      ...activeRoom,
      name: updates.name ?? activeRoom.name,
      uploadPolicy: updates.uploadPolicy ?? activeRoom.uploadPolicy,
      expiresAt,
    };

    return HttpResponse.json({ data: { ...activeRoom, joined: true } });
  }),

  http.delete(`${API_PREFIX}/rooms/:roomId`, ({ request, params }) => {
    if (request.headers.get("Authorization") === null) {
      return unauthorized();
    }

    if (Number(params.roomId) !== ACTIVE_ROOM_ID) {
      return roomNotFound();
    }

    activeRoom = { ...activeRoom, status: "DELETED" };

    return HttpResponse.json({
      data: {
        code: activeRoom.code,
        status: activeRoom.status,
        deletedAt: "2026-08-18T07:00:00Z",
        purgeAt: "2026-08-25T07:00:00Z",
      },
    });
  }),
];
