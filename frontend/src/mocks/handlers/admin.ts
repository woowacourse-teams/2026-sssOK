import { http, HttpResponse } from "msw";

import type { AdminAccount } from "@/entities/admin-account";
import { API_BASE_URL } from "@/shared/config";

/**
 * 목으로 로그인되는 유일한 계정. 실제 계정은 서버가 발급한다 (`V21__create_admin.sql`).
 *
 * 테스트가 이 값을 그대로 import 한다 — 양쪽에 따로 적어 두면 한쪽만 바꿨을 때
 * 조용히 401 이 나고, 원인이 로그인 로직처럼 보인다.
 */
export const ADMIN_CREDENTIALS = {
  loginId: "haeni",
  password: "sssok1234!",
  adminId: 4,
  name: "해니",
  role: "ADMIN",
} as const;

/**
 * 디자인(`Admin Accounts`)에 그려진 계정 그대로다. 로그인되는 목 계정(해니)도 여기 들어 있어야
 * 목록에서 "나" 표시가 붙는다.
 */
export const ADMIN_ACCOUNTS: AdminAccount[] = [
  {
    adminId: 1,
    loginId: "superadmin",
    name: "마이찬",
    role: "SUPER_ADMIN",
    createdAt: "2026-09-17T01:00:00Z",
  },
  {
    adminId: 2,
    loginId: "hyeonmibap",
    name: "현미밥",
    role: "ADMIN",
    createdAt: "2026-09-17T01:10:00Z",
  },
  { adminId: 3, loginId: "yundol", name: "윤돌", role: "ADMIN", createdAt: "2026-09-17T01:20:00Z" },
  {
    adminId: ADMIN_CREDENTIALS.adminId,
    loginId: ADMIN_CREDENTIALS.loginId,
    name: ADMIN_CREDENTIALS.name,
    role: ADMIN_CREDENTIALS.role,
    createdAt: "2026-09-17T01:30:00Z",
  },
];

/** 서버 기본값과 맞춘다 (`admin.login.max-failures`, `admin.login.lock-duration`). */
const MAX_FAILURES = 5;
const LOCK_SECONDS = 300;

let failureCount = 0;
let lockedUntil: number | null = null;

/** 테스트끼리 실패 횟수가 이어지지 않도록 되돌린다. */
export const resetAdminLoginAttempts = () => {
  failureCount = 0;
  lockedUntil = null;
};

/** 429 를 바로 재현하고 싶을 때 쓴다 — 실패를 다섯 번 흉내 내지 않아도 된다. */
export const lockAdminLogin = (seconds: number) => {
  lockedUntil = Date.now() + seconds * 1000;
};

export const adminHandlers = [
  http.post(`${API_BASE_URL}/admin/auth/login`, async ({ request }) => {
    const body = (await request.json().catch(() => null)) as {
      loginId?: string;
      password?: string;
    } | null;

    if (!body?.loginId || !body.password) {
      return HttpResponse.json(
        { code: "INVALID_REQUEST_BODY", message: "요청 본문 형식이 올바르지 않습니다" },
        { status: 400 },
      );
    }

    if (lockedUntil !== null) {
      const remaining = Math.ceil((lockedUntil - Date.now()) / 1000);

      if (remaining > 0) {
        return HttpResponse.json(
          {
            code: "ADMIN_LOGIN_RATE_LIMITED",
            message: "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요",
          },
          { status: 429, headers: { "Retry-After": String(remaining) } },
        );
      }

      // 잠금이 풀리면 횟수부터 되돌린다 — 안 그러면 한 번만 더 틀려도 바로 다시 잠긴다.
      resetAdminLoginAttempts();
    }

    const matched =
      body.loginId === ADMIN_CREDENTIALS.loginId && body.password === ADMIN_CREDENTIALS.password;

    if (!matched) {
      failureCount += 1;

      if (failureCount >= MAX_FAILURES) {
        lockAdminLogin(LOCK_SECONDS);
      }

      // 아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다 —
      // 구분해 주면 어떤 아이디가 존재하는지 알려주는 꼴이 된다.
      return HttpResponse.json(
        { code: "INVALID_CREDENTIALS", message: "아이디 또는 비밀번호가 올바르지 않습니다" },
        { status: 401 },
      );
    }

    resetAdminLoginAttempts();

    return HttpResponse.json({
      data: {
        accessToken: "mock-admin-token",
        adminId: ADMIN_CREDENTIALS.adminId,
        loginId: ADMIN_CREDENTIALS.loginId,
        name: ADMIN_CREDENTIALS.name,
        role: ADMIN_CREDENTIALS.role,
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
      },
    });
  }),

  http.get(`${API_BASE_URL}/admin/accounts`, ({ request }) => {
    if (!request.headers.get("Authorization")?.startsWith("Bearer ")) {
      return HttpResponse.json(
        { code: "UNAUTHORIZED", message: "인증이 필요합니다" },
        { status: 401 },
      );
    }

    return HttpResponse.json({ data: { accounts: ADMIN_ACCOUNTS } });
  }),
];
