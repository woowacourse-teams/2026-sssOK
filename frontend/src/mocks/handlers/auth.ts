import { http, HttpResponse } from "msw";

import { API_PREFIX } from "../config";

/** 첫 회원은 방 목 데이터의 hostId 와 같다 — 처음 인증한 사람이 방장으로 보인다. */
const FIRST_USER_ID = 10234;

/** 새로고침해도 번호가 이어지도록 저장소에 둔다. 목 전용 값이라 앱은 읽지 않는다. */
const COUNTER_KEY = "sssok.mock.nextUserId";

const readNextUserId = () => Number(localStorage.getItem(COUNTER_KEY)) || FIRST_USER_ID;

/**
 * 실제 서버는 호출할 때마다 새 member 를 만든다.
 * 방마다 인증을 새로 하는 구조라, 목도 매번 다른 userId·토큰을 줘야 방별로 다른 사람이 된다.
 */
export const authHandlers = [
  http.post(`${API_PREFIX}/auth/anonymous`, async ({ request }) => {
    const { nickname } = (await request.json()) as { nickname: string };
    const userId = readNextUserId();

    localStorage.setItem(COUNTER_KEY, String(userId + 1));

    return HttpResponse.json(
      {
        data: {
          accessToken: `mock-token-${userId}`,
          userId,
          nickname,
          expiresAt: "2026-09-17T05:30:00Z",
        },
      },
      { status: 201 },
    );
  }),
];
