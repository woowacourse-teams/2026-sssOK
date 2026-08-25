import { http, HttpResponse } from "msw";

import { API_PREFIX } from "../config";

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
  /** 형식 자체가 틀린 코드 (O 는 허용 알파벳이 아니다) → 400 */
  invalid: "NOTFOUND",
} as const;

const room = (code: string, status: "ACTIVE" | "EXPIRED" | "DELETED") => ({
  roomId: 5031,
  code,
  name: "제주 여행",
  status,
  hostId: 10234,
  hostName: "민수",
  uploadPolicy: "everyone",
  joined: false,
  createdAt: "2026-08-18T05:30:00Z",
  expiresAt: status === "ACTIVE" ? "2026-09-30T05:30:00Z" : "2026-08-01T05:30:00Z",
});

export const roomHandlers = [
  // 만료·삭제된 방도 404 가 아니라 200 + status 로 내려온다.
  http.get(`${API_PREFIX}/rooms/:code`, ({ params }) => {
    const code = String(params.code);

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
      return HttpResponse.json({ data: room(code, "ACTIVE") });
    }

    return HttpResponse.json(
      { code: "ROOM_NOT_FOUND", message: "존재하지 않는 방입니다." },
      { status: 404 },
    );
  }),

  http.post(`${API_PREFIX}/rooms`, async ({ request }) => {
    const { name } = (await request.json()) as { name: string };

    return HttpResponse.json(
      { data: { ...room(MOCK_ROOM_CODES.active, "ACTIVE"), name } },
      { status: 201 },
    );
  }),
];
