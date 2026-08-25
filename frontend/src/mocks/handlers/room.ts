import { http, HttpResponse } from "msw";

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
  /** 형식 자체가 틀린 코드 (O 는 허용 알파벳이 아니다) → 400 */
  invalid: "NOTFOUND",
} as const;

const room = (code: string, status: "ACTIVE" | "EXPIRED" | "DELETED", joined = false) => ({
  roomId: 5031,
  code,
  name: "제주 여행",
  status,
  hostId: 10234,
  hostName: "민수",
  uploadPolicy: "everyone",
  joined,
  createdAt: "2026-08-18T05:30:00Z",
  expiresAt: status === "ACTIVE" ? "2026-09-30T05:30:00Z" : "2026-08-01T05:30:00Z",
});

/** 입장 멱등성을 흉내내려고 이번 세션의 입장 기록을 들고 있는다. 목 전용 상태다. */
const joinedRooms = new Set<string>();

/** 테스트끼리 입장 기록이 이어지지 않도록 되돌린다. */
export const resetJoinedRooms = () => joinedRooms.clear();

export const roomHandlers = [
  // 만료·삭제된 방도 404 가 아니라 200 + status 로 내려온다.
  http.get("/rooms/:code", ({ request, params }) => {
    const code = String(params.code);
    const token = request.headers.get("Authorization");
    // 토큰이 실렸을 때만 참여 여부를 판정한다. 비로그인 요청은 언제나 false 다.
    const joined = token !== null && joinedRooms.has(`${token}:${code}`);

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

    if (code === MOCK_ROOM_CODES.active || code === MOCK_ROOM_CODES.second) {
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
  http.post("/rooms/:roomCode/members", ({ request, params }) => {
    const token = request.headers.get("Authorization");

    if (token === null) {
      return HttpResponse.json(
        { code: "UNAUTHORIZED", message: "인증이 필요합니다." },
        { status: 401 },
      );
    }

    const roomCode = String(params.roomCode);
    const alreadyJoined = joinedRooms.has(`${token}:${roomCode}`);

    joinedRooms.add(`${token}:${roomCode}`);

    return HttpResponse.json(
      {
        memberId: 10234,
        displayName: "해니",
        hostId: 10234,
        joinedAt: "2026-08-18T05:31:00Z",
      },
      { status: alreadyJoined ? 200 : 201 },
    );
  }),

  http.post("/rooms", async ({ request }) => {
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
        roomId: 5031,
        code: MOCK_ROOM_CODES.active,
        name,
        hostId: 10234,
        hostName: "민수",
        createdAt: "2026-08-18T05:30:00Z",
        expiresAt: "2026-08-19T05:30:00Z",
        uploadPolicy,
      },
      { status: 201 },
    );
  }),
];
