import { http, HttpResponse } from "msw";

import { API_BASE_URL } from "@/shared/config";

/** 첫 회원은 방 목 데이터의 hostId 와 같다 — 처음 인증한 사람이 방장으로 보인다. */
const FIRST_USER_ID = 10234;

/** 새로고침해도 번호가 이어지도록 저장소에 둔다. 목 전용 값이라 앱은 읽지 않는다. */
const COUNTER_KEY = "sssok.mock.nextUserId";

const readNextUserId = () => Number(localStorage.getItem(COUNTER_KEY)) || FIRST_USER_ID;

/**
 * 인증할 때 받은 이름을 기억한다. 업로드 목이 등록 응답의 uploaderName 을 채우는 데 쓴다.
 * 인증을 거치지 않고 손으로 만든 토큰은 여기 없으니 부르는 쪽에서 대비해야 한다.
 */
const nicknameByUserId = new Map<number, string>();

/** 인증을 거치지 않은 회원 번호면 null 이다. */
export const nicknameOf = (userId: number) => nicknameByUserId.get(userId) ?? null;

/** 테스트끼리 이름이 이어지지 않도록 되돌린다. */
export const resetNicknames = () => nicknameByUserId.clear();

/**
 * 실제 서버는 호출할 때마다 새 member 를 만든다.
 * 방마다 인증을 새로 하는 구조라, 목도 매번 다른 userId·토큰을 줘야 방별로 다른 사람이 된다.
 */
export const authHandlers = [
  http.post(`${API_BASE_URL}/auth/anonymous`, async ({ request }) => {
    const { nickname } = (await request.json()) as { nickname: string };
    const userId = readNextUserId();

    localStorage.setItem(COUNTER_KEY, String(userId + 1));
    nicknameByUserId.set(userId, nickname);

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
