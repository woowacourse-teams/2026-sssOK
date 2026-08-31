import { http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";
import {
  isDeletedMedia,
  originalUrlOf,
  registeredMediaOf,
  resetDeletedMedia,
  thumbnailUrlOf,
  type GalleryMedia,
} from "../db";

/**
 * 방 코드는 8자리다. 혼동하기 쉬운 0, 1, I, O 는 알파벳에서 빠져 있다.
 * backend 의 RoomCode 값 객체와 같은 규칙이다.
 */
const ROOM_CODE_PATTERN = /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/;
const HOUR_IN_MILLISECONDS = 60 * 60 * 1000;

/** 시나리오별 고정 코드. 테스트와 수동 확인에서 함께 쓴다. */
export const MOCK_ROOM_CODES = {
  active: "7K93QX2S",
  expired: "EXPRED77",
  deleted: "DELETED7",
  /** 형식은 맞지만 존재하지 않는 방 → 404 */
  notFound: "NTFUND23",
  /** 두 번째 활성 방. 방마다 다른 이름으로 들어가는 흐름을 손으로 확인할 때 쓴다. */
  second: "QRST6789",
  /** 방장만 올릴 수 있는 방. 업로드 권한 거절(403)을 손으로 확인할 때 쓴다. */
  hostOnly: "HSTNLY23",
  /** 보존 기간이 지나 영구 삭제된 방 */
  purged: "PURGED77",
  /** 형식 자체가 틀린 코드 (O 는 허용 알파벳이 아니다) → 400 */
  invalid: "NOTFOUND",
} as const;

/**
 * 방 번호는 코드마다 다르다 — 실제 서버와 같다.
 * 조회 응답의 roomId 로 이후 경로(`/rooms/{roomId}/...`)와 스토리지 키가 만들어진다.
 */
const ROOM_IDS: Record<string, number> = {
  [MOCK_ROOM_CODES.active]: 5031,
  [MOCK_ROOM_CODES.second]: 5032,
  [MOCK_ROOM_CODES.expired]: 5033,
  [MOCK_ROOM_CODES.deleted]: 5034,
  [MOCK_ROOM_CODES.hostOnly]: 5035,
  [MOCK_ROOM_CODES.purged]: 5036,
};

const ROOM_EXPIRY_HOURS: Record<string, 24 | 72> = {
  [MOCK_ROOM_CODES.active]: 24,
  [MOCK_ROOM_CODES.second]: 72,
  [MOCK_ROOM_CODES.hostOnly]: 24,
};

const createRoomExpiresAt = () => {
  const now = Date.now();

  return Object.fromEntries(
    Object.entries(ROOM_EXPIRY_HOURS).map(([code, expiryHours]) => [
      code,
      new Date(now + expiryHours * HOUR_IN_MILLISECONDS).toISOString(),
    ]),
  ) as Record<string, string>;
};

let roomExpiresAt = createRoomExpiresAt();

/**
 * 방마다 가진 폴더. backend `RoomFolderResponse` 와 같은 모양이다.
 * 업로드 목이 발급 요청의 folderIds 를 이 목록과 대조한다.
 */
const ROOM_FOLDERS: Record<string, { id: number; name: string; photoCount: number }[]> = {
  [MOCK_ROOM_CODES.active]: [
    { id: 31, name: "첫째 날", photoCount: 12 },
    { id: 32, name: "둘째 날", photoCount: 4 },
  ],
};

const foldersOf = (code: string) => ROOM_FOLDERS[code] ?? [];

/** 기본 방(active)의 폴더 번호. 테스트와 수동 확인이 쓴다. */
export const MOCK_FOLDER_IDS = foldersOf(MOCK_ROOM_CODES.active).map((folder) => folder.id);

/** 목이 아는 방장. 방 데이터의 hostId 이자 auth 목의 첫 회원이다. */
export const MOCK_HOST_ID = 10234;

/** 방마다 업로드 권한이 다르다. backend UploadPolicy 의 apiValue 와 같은 표기다. */
const UPLOAD_POLICIES: Record<string, "everyone" | "host"> = {
  [MOCK_ROOM_CODES.hostOnly]: "host",
};

const uploadPolicyOf = (code: string) => UPLOAD_POLICIES[code] ?? "everyone";

/** 기본 방(active) 번호. 업로드 목과 수동 확인이 이 방을 쓴다. */
export const MOCK_ROOM_ID = ROOM_IDS[MOCK_ROOM_CODES.active];

/** 방 번호 → 코드. zip 파일명이 코드를 쓴다 (`ShareDrop_{roomCode}.zip`). */
export const roomCodeOfId = (roomId: number) =>
  Object.entries(ROOM_IDS).find(([, id]) => id === roomId)?.[0] ?? null;

/** 들어갈 수 있는 방. 만료·삭제된 방은 여기 없다. */
const ACTIVE_ROOM_CODES = [
  MOCK_ROOM_CODES.active,
  MOCK_ROOM_CODES.second,
  MOCK_ROOM_CODES.hostOnly,
] as string[];

const isActiveRoomCode = (code: string) => ACTIVE_ROOM_CODES.includes(code);

const codeOfRoomId = (roomId: number) =>
  Object.keys(ROOM_IDS).find((code) => ROOM_IDS[code] === roomId) ?? null;

/** 업로드처럼 방 번호로 들어오는 요청이 열려 있는 방을 가리키는지 본다. */
export const isActiveRoomId = (roomId: number) =>
  ACTIVE_ROOM_CODES.some((code) => ROOM_IDS[code] === roomId);

/**
 * 방 번호로 상태를 본다. 모르는 번호면 null 이다.
 * 업로드 목이 404(없는 방)와 410(만료·삭제)을 갈라 내려주려고 쓴다.
 */
export const roomStatusOfId = (roomId: number): RoomStatus | null => {
  const code = codeOfRoomId(roomId);

  if (code === null) {
    return null;
  }
  if (code === MOCK_ROOM_CODES.expired) {
    return "EXPIRED";
  }
  if (code === MOCK_ROOM_CODES.deleted) {
    return "DELETED";
  }
  if (code === MOCK_ROOM_CODES.purged) {
    return "PURGED";
  }
  if (code === MOCK_ROOM_CODES.active && activeRoomOverrides.status) {
    return activeRoomOverrides.status;
  }

  return "ACTIVE";
};

/** 업로드 목이 발급 요청의 folderIds 를 확인할 때 쓴다. 모르는 방이면 폴더도 없다. */
export const hasFolder = (roomId: number, folderId: number) => {
  const code = codeOfRoomId(roomId);

  return code !== null && foldersOf(code).some((folder) => folder.id === folderId);
};

/** 방 번호로 업로드 권한을 본다. 모르는 번호면 null 이다. */
export const uploadPolicyOfId = (roomId: number) => {
  const code = codeOfRoomId(roomId);

  return code === null ? null : uploadPolicyOf(code);
};

/** backend RoomResponse 의 status 와 같다. PURGED 는 보존 기간이 지나 영구 삭제된 방이다. */
type RoomStatus = "ACTIVE" | "EXPIRED" | "DELETED" | "PURGED";

/** 업로드 목이 등록한 미디어와 같은 모양이어야 해서 정의를 `mocks/db.ts` 에 둔다. */
type MockMedia = GalleryMedia;

interface ActiveRoomOverrides {
  name?: string;
  status?: RoomStatus;
  uploadPolicy?: "everyone" | "host";
  expiresAt?: string;
}

let activeRoomOverrides: ActiveRoomOverrides = {};

const room = (code: string, status: RoomStatus, joined = false) => {
  const roomId = ROOM_IDS[code];
  const uploaded = registeredMediaOf(roomId);
  const seeded = roomId === MOCK_ROOM_ID ? mediaItems : [];
  const countChange = (folderId?: number) => {
    const inFolder = (media: MockMedia) =>
      folderId === undefined || media.folderIds.includes(folderId);
    return (
      uploaded.filter(inFolder).length -
      [...seeded, ...uploaded].filter(
        (media) => inFolder(media) && isDeletedMedia(roomId, media.mediaId),
      ).length
    );
  };
  const response = {
    roomId: ROOM_IDS[code],
    code,
    name: "제주 여행",
    status,
    hostId: MOCK_HOST_ID,
    hostName: "민수",
    uploadPolicy: uploadPolicyOf(code),
    joined,
    createdAt: "2026-08-18T05:30:00Z",
    expiresAt:
      status === "ACTIVE"
        ? roomExpiresAt[code]
        : new Date(Date.now() - 24 * HOUR_IN_MILLISECONDS).toISOString(),
    /** 폴더 소속과 무관한 방 전체 사진 수. 갓 만든 방은 0 이다. */
    photoCount: Math.max(
      0,
      foldersOf(code).reduce((sum, folder) => sum + folder.photoCount, 0) + countChange(),
    ),
    /** 생성 순 폴더 목록. 갓 만든 방은 빈 배열이다. */
    folders: foldersOf(code).map((folder) => ({
      ...folder,
      photoCount: Math.max(0, folder.photoCount + countChange(folder.id)),
    })),
  };

  return code === MOCK_ROOM_CODES.active ? { ...response, ...activeRoomOverrides } : response;
};

const createMedia = ({
  mediaId,
  type = "IMAGE",
  fileName,
  folderIds = [],
  uploaderId,
  uploaderName,
  duration = null,
}: Pick<MockMedia, "mediaId" | "fileName" | "uploaderId" | "uploaderName"> &
  Partial<Pick<MockMedia, "type" | "folderIds" | "duration">>): MockMedia => ({
  mediaId,
  type,
  fileName,
  mimeType: type === "VIDEO" ? "video/mp4" : "image/jpeg",
  size: type === "VIDEO" ? 182452224 : 2912048,
  thumbnailUrl: thumbnailUrlOf(mediaId),
  originalUrl: originalUrlOf(mediaId, type),
  width: type === "VIDEO" ? 1920 : 3024,
  height: type === "VIDEO" ? 1080 : 4032,
  duration,
  folderIds,
  uploaderId,
  uploaderName,
  status: "READY",
  uploadedAt: `2026-08-18T${String(18 + Math.floor((mediaId - 5000) / 6)).padStart(2, "0")}:00:00+09:00`,
});

const mediaItems: MockMedia[] = [
  createMedia({
    mediaId: 5012,
    fileName: "IMG_0421.jpg",
    folderIds: [31],
    uploaderId: MOCK_HOST_ID,
    uploaderName: "민수",
  }),
  createMedia({
    mediaId: 5011,
    type: "VIDEO",
    fileName: "VID_0032.mp4",
    uploaderId: 7,
    uploaderName: "미미",
    duration: 34,
  }),
  createMedia({
    mediaId: 5010,
    fileName: "IMG_0419.jpg",
    folderIds: [31],
    uploaderId: MOCK_HOST_ID,
    uploaderName: "민수",
  }),
  createMedia({
    mediaId: 5009,
    fileName: "IMG_0418.jpg",
    folderIds: [31],
    uploaderId: 21,
    uploaderName: "포키",
  }),
  createMedia({
    mediaId: 5008,
    type: "VIDEO",
    fileName: "VID_0031.mp4",
    folderIds: [31],
    uploaderId: 7,
    uploaderName: "미미",
    duration: 58,
  }),
  createMedia({
    mediaId: 5007,
    fileName: "IMG_0417.jpg",
    folderIds: [32],
    uploaderId: MOCK_HOST_ID,
    uploaderName: "민수",
  }),
  createMedia({
    mediaId: 5006,
    fileName: "IMG_0416.jpg",
    folderIds: [32],
    uploaderId: 12,
    uploaderName: "로지",
  }),
  createMedia({
    mediaId: 5005,
    fileName: "IMG_0415.jpg",
    uploaderId: 21,
    uploaderName: "포키",
  }),
  createMedia({
    mediaId: 5004,
    type: "VIDEO",
    fileName: "VID_0030.mp4",
    uploaderId: MOCK_HOST_ID,
    uploaderName: "민수",
    duration: 72,
  }),
  createMedia({
    mediaId: 5003,
    fileName: "IMG_0414.jpg",
    uploaderId: 7,
    uploaderName: "미미",
  }),
  createMedia({
    mediaId: 5002,
    fileName: "IMG_0413.jpg",
    uploaderId: MOCK_HOST_ID,
    uploaderName: "민수",
  }),
  createMedia({
    mediaId: 5001,
    fileName: "IMG_0412.jpg",
    uploaderId: 12,
    uploaderName: "로지",
  }),
  createMedia({
    mediaId: 5000,
    fileName: "IMG_0411.jpg",
    uploaderId: 21,
    uploaderName: "포키",
  }),
];

/**
 * 입장 멱등성을 흉내내려고 입장 기록을 들고 있는다. 목 전용 상태다.
 *
 * **저장소에 둔다.** 메모리에만 두면 새로고침에서 기록만 사라지고 세션(`sssok.auth:*`)은
 * 남아, "토큰은 멀쩡한데 멤버가 아닌" 상태에 갇힌다 — 입장 화면은 세션이 있으니 갤러리로
 * 그냥 넘겨서 다시 입장할 길도 없다. 실제 서버는 입장 기록이 남으니 목도 그래야 한다.
 * `sssok.mock.nextUserId` 와 같은 이유다.
 */
const JOINED_ROOMS_KEY = "sssok.mock.joinedRooms";

const readJoinedRooms = (): Set<string> => {
  try {
    const raw = localStorage.getItem(JOINED_ROOMS_KEY);

    return new Set(raw === null ? [] : (JSON.parse(raw) as string[]));
  } catch {
    // 손상된 값은 되살릴 방법이 없다. 아무도 입장하지 않은 것으로 친다.
    return new Set();
  }
};

const writeJoinedRooms = (rooms: Set<string>) =>
  localStorage.setItem(JOINED_ROOMS_KEY, JSON.stringify([...rooms]));

/** 토큰마다 따로 센다. 방 번호로 남겨야 조회 핸들러와 키가 맞는다. */
const joinKey = (token: string, roomId: number) => `${token}:${roomId}`;

/**
 * 방에 있는 미디어 전부. **이번 세션에 올린 것이 앞에 온다** —
 * 갤러리는 최신순이고, 방금 올린 사진이 맨 위여야 한다.
 * 다운로드 목도 mediaId 로 원본을 찾을 때 이걸 쓴다.
 */
export const mediaOfRoom = (roomId: number) =>
  (roomId === MOCK_ROOM_ID
    ? [...registeredMediaOf(roomId), ...mediaItems]
    : registeredMediaOf(roomId)
  ).filter((media) => !isDeletedMedia(roomId, media.mediaId));

/** 테스트끼리 입장 기록이 이어지지 않도록 되돌린다. */
export const resetJoinedRooms = () => localStorage.removeItem(JOINED_ROOMS_KEY);

export const resetRoomHandlers = () => {
  resetJoinedRooms();
  resetDeletedMedia();
  activeRoomOverrides = {};
  roomExpiresAt = createRoomExpiresAt();
};

const unauthorized = () =>
  HttpResponse.json({ code: "UNAUTHORIZED", message: "인증이 필요합니다." }, { status: 401 });

const roomNotFound = () =>
  HttpResponse.json(
    { code: "ROOM_NOT_FOUND", message: "존재하지 않는 방입니다." },
    { status: 404 },
  );

/** 업로드처럼 참여자만 부를 수 있는 API 가 입장 여부를 확인할 때 쓴다. */
export const hasJoinedRoom = (token: string, roomId: number) =>
  readJoinedRooms().has(joinKey(token, roomId));

export const roomHandlers = [
  // 만료·삭제된 방도 404 가 아니라 200 + status 로 내려온다.
  http.get(`${API_BASE_URL}/rooms/:code`, ({ request, params }) => {
    const code = String(params.code);
    const token = request.headers.get("Authorization");

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

    if (code === MOCK_ROOM_CODES.purged) {
      return HttpResponse.json({ data: room(code, "PURGED") });
    }

    if (isActiveRoomCode(code)) {
      // 입장 기록은 방 번호로 남으니 (`POST /rooms/:roomId/members`) 조회도 같은 키로 본다.
      // 토큰이 실렸을 때만 판정한다. 비로그인 요청은 언제나 false 다.
      const joined = token !== null && hasJoinedRoom(token, ROOM_IDS[code]);

      return HttpResponse.json({ data: room(code, "ACTIVE", joined) });
    }

    return roomNotFound();
  }),

  http.get(`${API_BASE_URL}/rooms/:roomId/media`, ({ request, params }) => {
    if (request.headers.get("Authorization") === null) {
      return unauthorized();
    }

    const roomId = Number(params.roomId);

    if (roomId !== MOCK_ROOM_ID) {
      return roomNotFound();
    }

    return HttpResponse.json({ data: { items: mediaOfRoom(roomId) } });
  }),

  /**
   * 입장은 멱등이다. 처음이면 201, 이미 입장했으면 200 으로 같은 내용을 돌려준다.
   * 목은 이번 세션에 입장한 방을 기억해 두 번째 호출부터 200 을 준다.
   */
  http.post(`${API_BASE_URL}/rooms/:roomId/members`, ({ request, params }) => {
    const token = request.headers.get("Authorization");

    if (token === null) {
      return HttpResponse.json(
        { code: "UNAUTHORIZED", message: "인증이 필요합니다." },
        { status: 401 },
      );
    }

    const roomId = Number(params.roomId);
    const joinedRooms = readJoinedRooms();
    const alreadyJoined = joinedRooms.has(joinKey(token, roomId));

    joinedRooms.add(joinKey(token, roomId));
    writeJoinedRooms(joinedRooms);

    return HttpResponse.json(
      {
        data: {
          roomId,
          userId: 10234,
          displayName: "해니",
          hostId: MOCK_HOST_ID,
          joinedAt: "2026-08-18T05:31:00Z",
        },
      },
      { status: alreadyJoined ? 200 : 201 },
    );
  }),

  http.post(`${API_BASE_URL}/rooms`, async ({ request }) => {
    // 토큰은 인증할 때마다 달라진다. 목은 실렸는지만 본다.
    if (request.headers.get("Authorization") === null) {
      return HttpResponse.json({ message: "인증이 필요합니다." }, { status: 401 });
    }

    const { name, uploadPolicy, expiryHours } = (await request.json()) as {
      name: string;
      uploadPolicy: "everyone" | "host";
      expiryHours: 24 | 72;
    };

    const now = Date.now();

    return HttpResponse.json(
      {
        data: {
          roomId: MOCK_ROOM_ID,
          code: MOCK_ROOM_CODES.active,
          name,
          hostId: MOCK_HOST_ID,
          hostName: "민수",
          createdAt: new Date(now).toISOString(),
          expiresAt: new Date(now + expiryHours * HOUR_IN_MILLISECONDS).toISOString(),
          uploadPolicy,
          // 갓 만든 방이라 사진도 폴더도 없다.
          photoCount: 0,
          folders: [],
        },
      },
      { status: 201 },
    );
  }),

  http.patch(`${API_BASE_URL}/rooms/:roomId`, async ({ request, params }) => {
    if (request.headers.get("Authorization") === null) {
      return unauthorized();
    }

    if (Number(params.roomId) !== MOCK_ROOM_ID) {
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

    activeRoomOverrides = {
      ...activeRoomOverrides,
      ...(updates.name !== undefined && { name: updates.name }),
      ...(updates.uploadPolicy !== undefined && { uploadPolicy: updates.uploadPolicy }),
      ...(updates.expiryHours !== undefined && {
        expiresAt: new Date(Date.now() + updates.expiryHours * HOUR_IN_MILLISECONDS).toISOString(),
      }),
    };

    return HttpResponse.json({ data: room(MOCK_ROOM_CODES.active, "ACTIVE", true) });
  }),

  http.delete(`${API_BASE_URL}/rooms/:roomId`, ({ request, params }) => {
    if (request.headers.get("Authorization") === null) {
      return unauthorized();
    }

    if (Number(params.roomId) !== MOCK_ROOM_ID) {
      return roomNotFound();
    }

    activeRoomOverrides = { ...activeRoomOverrides, status: "DELETED" };

    return HttpResponse.json({
      data: {
        code: MOCK_ROOM_CODES.active,
        status: "DELETED",
        deletedAt: "2026-08-18T07:00:00Z",
        purgeAt: "2026-08-25T07:00:00Z",
      },
    });
  }),
];
