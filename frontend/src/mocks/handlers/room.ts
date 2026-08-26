import { http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";

/**
 * 방 코드는 8자리다. 혼동하기 쉬운 0, 1, I, O 는 알파벳에서 빠져 있다.
 * backend 의 RoomCode 값 객체와 같은 규칙이다.
 */
const ROOM_CODE_PATTERN = /^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$/;

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

const room = (code: string, status: RoomStatus, joined = false) => ({
  roomId: ROOM_IDS[code],
  code,
  name: "제주 여행",
  status,
  hostId: MOCK_HOST_ID,
  hostName: "민수",
  uploadPolicy: uploadPolicyOf(code),
  joined,
  createdAt: "2026-08-18T05:30:00Z",
  expiresAt: status === "ACTIVE" ? "2026-09-30T05:30:00Z" : "2026-08-01T05:30:00Z",
  /** 폴더 소속과 무관한 방 전체 사진 수. 갓 만든 방은 0 이다. */
  photoCount: foldersOf(code).reduce((sum, folder) => sum + folder.photoCount, 0),
  /** 생성 순 폴더 목록. 갓 만든 방은 빈 배열이다. */
  folders: foldersOf(code),
});

/** 입장 멱등성을 흉내내려고 이번 세션의 입장 기록을 들고 있는다. 목 전용 상태다. */
const joinedRooms = new Set<string>();

/** 토큰마다 따로 센다. 방 번호로 남겨야 조회 핸들러와 키가 맞는다. */
const joinKey = (token: string, roomId: number) => `${token}:${roomId}`;

/** 테스트끼리 입장 기록이 이어지지 않도록 되돌린다. */
export const resetJoinedRooms = () => joinedRooms.clear();

/** 업로드처럼 참여자만 부를 수 있는 API 가 입장 여부를 확인할 때 쓴다. */
export const hasJoinedRoom = (token: string, roomId: number) =>
  joinedRooms.has(joinKey(token, roomId));

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
      const joined = token !== null && joinedRooms.has(joinKey(token, ROOM_IDS[code]));

      return HttpResponse.json({ data: room(code, "ACTIVE", joined) });
    }

    return HttpResponse.json(
      { code: "ROOM_NOT_FOUND", message: "존재하지 않는 방입니다." },
      { status: 404 },
    );
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
    const alreadyJoined = joinedRooms.has(joinKey(token, roomId));

    joinedRooms.add(joinKey(token, roomId));

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

    const { name, uploadPolicy } = (await request.json()) as {
      name: string;
      uploadPolicy: "everyone" | "host";
      expiryHours: 24 | 72;
    };

    return HttpResponse.json(
      {
        data: {
          roomId: MOCK_ROOM_ID,
          code: MOCK_ROOM_CODES.active,
          name,
          hostId: MOCK_HOST_ID,
          hostName: "민수",
          createdAt: "2026-08-18T05:30:00Z",
          expiresAt: "2026-08-19T05:30:00Z",
          uploadPolicy,
          // 갓 만든 방이라 사진도 폴더도 없다.
          photoCount: 0,
          folders: [],
        },
      },
      { status: 201 },
    );
  }),
];
