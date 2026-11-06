import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { delay, http, HttpResponse } from "msw";
import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { getRoomSession, saveRoomSession } from "@/entities/session";
import { server } from "@/mocks/server";
import { apiClient } from "@/shared/api";
import { API_BASE_URL } from "@/shared/config";
import { useUnauthorizedRecovery } from "./useUnauthorizedRecovery";

const session = {
  accessToken: "old",
  userId: 1,
  nickname: "이름",
  memberStatus: "REGISTERED" as const,
  expiresAt: "2099-01-01T00:00:00Z",
  refreshExpiresAt: "2099-02-01T00:00:00Z",
};
const setup = (anonymous = false) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  if (anonymous) saveRoomSession("ROOM", session);
  else client.setQueryData(MEMBER_SESSION_QUERY_KEY, session);
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/rooms/ROOM/gallery"]}>{children}</MemoryRouter>
    </QueryClientProvider>
  );
  const hook = renderHook(
    () => {
      useUnauthorizedRecovery();
      return useLocation().pathname;
    },
    { wrapper },
  );
  server.use(
    http.post(`${API_BASE_URL}/recovery-test`, ({ request }) =>
      request.headers.get("Authorization") === "Bearer new"
        ? HttpResponse.json({ data: "ok" })
        : new HttpResponse(null, { status: 401 }),
    ),
  );
  return { client, ...hook };
};
const request = () =>
  apiClient("/recovery-test", { method: "POST", token: "old", body: JSON.stringify({ value: 1 }) });
afterEach(() => {
  localStorage.clear();
  delete window.ReactNativeWebView;
});

it.each([false, true])(
  "회원/익명 재발급 후 원래 요청을 재시도하고 세션을 갱신한다: %s",
  async (anonymous) => {
    const { client } = setup(anonymous);
    server.use(
      http.post(`${API_BASE_URL}/auth/refresh`, ({ request }) => {
        expect(request.credentials).toBe("include");
        expect(request.headers.get("X-CSRF-Protection")).toBe("1");
        return HttpResponse.json({
          data: {
            ...session,
            accessToken: "new",
            memberStatus: anonymous ? "ANONYMOUS" : "REGISTERED",
          },
        });
      }),
    );
    await act(async () => {
      await expect(request()).resolves.toBe("ok");
    });
    expect(
      anonymous
        ? getRoomSession("ROOM")?.accessToken
        : client.getQueryData<typeof session>(MEMBER_SESSION_QUERY_KEY)?.accessToken,
    ).toBe("new");
    if (anonymous) expect(getRoomSession("ROOM")).not.toHaveProperty("memberStatus");
  },
);

it.each([false, true])("복구 불가 시 플랫폼별 화면으로 이동한다: native=%s", async (native) => {
  if (native) window.ReactNativeWebView = { postMessage: jest.fn() };
  const { result, client } = setup();
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => new HttpResponse(null, { status: 401 })),
  );
  await act(async () => {
    await expect(request()).rejects.toMatchObject({ status: 401 });
  });
  expect(result.current).toBe(native ? "/app/login" : "/home");
  expect(client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toBeNull();
});

it("동시 401과 늦은 이전 토큰 요청은 refresh 하나를 공유한다", async () => {
  setup();
  let count = 0;
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, async () => {
      count++;
      await delay(20);
      return HttpResponse.json({ data: { ...session, accessToken: "new" } });
    }),
  );
  await act(async () => {
    await expect(Promise.all([request(), request(), request()])).resolves.toEqual([
      "ok",
      "ok",
      "ok",
    ]);
    await expect(request()).resolves.toBe("ok");
  });
  expect(count).toBe(1);
});

it.each([500, 429])("refresh %s에서는 세션과 화면을 유지한다", async (status) => {
  const { result, client } = setup();
  server.use(http.post(`${API_BASE_URL}/auth/refresh`, () => new HttpResponse(null, { status })));
  await act(async () => {
    await expect(request()).rejects.toMatchObject({ status });
  });
  expect(result.current).toBe("/rooms/ROOM/gallery");
  expect(client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toEqual(session);
});

it("다른 사용자의 토큰으로 원래 요청을 재시도하지 않는다", async () => {
  const { result } = setup();
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () =>
      HttpResponse.json({ data: { ...session, userId: 2, accessToken: "new" } }),
    ),
  );
  await act(async () => {
    await expect(request()).rejects.toMatchObject({ status: 401 });
  });
  expect(result.current).toBe("/home");
});

it("재발급 후 재차 401은 반복하지 않고 종료한다", async () => {
  const { result } = setup();
  let count = 0;
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => {
      count++;
      return HttpResponse.json({ data: { ...session, accessToken: "new" } });
    }),
    http.post(`${API_BASE_URL}/recovery-test`, () => new HttpResponse(null, { status: 401 })),
  );
  await act(async () => {
    await expect(request()).rejects.toMatchObject({ status: 401 });
  });
  expect(count).toBe(1);
  expect(result.current).toBe("/home");
});

it("403은 재발급하지 않고 세션을 유지한다", async () => {
  const { client } = setup();
  const refresh = jest.fn();
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => {
      refresh();
      return new HttpResponse(null, { status: 401 });
    }),
    http.post(`${API_BASE_URL}/recovery-test`, () => new HttpResponse(null, { status: 403 })),
  );
  await expect(request()).rejects.toMatchObject({ status: 403 });
  expect(refresh).not.toHaveBeenCalled();
  expect(client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toEqual(session);
});

it("재발급 중 로그아웃하면 늦은 응답을 적용하지 않는다", async () => {
  const { client, result } = setup();
  let release!: () => void;
  const waiting = new Promise<void>((resolve) => {
    release = resolve;
  });
  let started!: () => void;
  const ready = new Promise<void>((resolve) => {
    started = resolve;
  });
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, async () => {
      started();
      await waiting;
      return HttpResponse.json({ data: { ...session, accessToken: "new" } });
    }),
  );
  const outcome = request().catch((error) => error);
  await ready;
  await act(async () => {
    client.setQueryData(MEMBER_SESSION_QUERY_KEY, null);
    release();
    await outcome;
  });
  expect(client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toBeNull();
  expect(result.current).toBe("/rooms/ROOM/gallery");
});

it("관리자 토큰은 회원·익명 재발급을 하지 않는다", async () => {
  const { client } = setup();
  const refresh = jest.fn();
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => {
      refresh();
      return new HttpResponse(null, { status: 401 });
    }),
    http.get(`${API_BASE_URL}/admin/accounts`, () => new HttpResponse(null, { status: 401 })),
  );
  await expect(apiClient("/admin/accounts", { token: "admin-token" })).rejects.toMatchObject({
    status: 401,
  });
  expect(refresh).not.toHaveBeenCalled();
  expect(client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toEqual(session);
});

it("보호된 auth API도 요청 내용 그대로 재시도한다", async () => {
  setup();
  const received: string[] = [];
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () =>
      HttpResponse.json({ data: { ...session, accessToken: "new" } }),
    ),
    http.post(`${API_BASE_URL}/auth/link-code`, async ({ request }) => {
      received.push(await request.text());
      expect(request.headers.get("X-Test")).toBe("preserved");
      return request.headers.get("Authorization") === "Bearer new"
        ? HttpResponse.json({ data: "ok" })
        : new HttpResponse(null, { status: 401 });
    }),
  );
  await act(async () => {
    await expect(
      apiClient("/auth/link-code", {
        method: "POST",
        token: "old",
        headers: { "X-Test": "preserved" },
        body: "request-body",
      }),
    ).resolves.toBe("ok");
  });
  expect(received).toEqual(["request-body", "request-body"]);
});

it("여러 번 갱신해도 이전 토큰 요청은 최신 토큰을 사용한다", async () => {
  const { client } = setup();
  let count = 0;
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => {
      count++;
      return HttpResponse.json({ data: { ...session, accessToken: `renewed-${count}` } });
    }),
    http.post(`${API_BASE_URL}/recovery-test`, ({ request }) =>
      request.headers.get("Authorization") === "Bearer renewed-1"
        ? HttpResponse.json({ data: "ok" })
        : new HttpResponse(null, { status: 401 }),
    ),
  );
  await act(async () => {
    await expect(request()).resolves.toBe("ok");
  });
  server.use(
    http.post(`${API_BASE_URL}/recovery-test`, ({ request }) =>
      request.headers.get("Authorization") === "Bearer renewed-2"
        ? HttpResponse.json({ data: "ok" })
        : new HttpResponse(null, { status: 401 }),
    ),
  );
  await act(async () => {
    await expect(apiClient("/recovery-test", { method: "POST", token: "renewed-1" })).resolves.toBe(
      "ok",
    );
    await expect(request()).resolves.toBe("ok");
  });
  expect(count).toBe(2);
  expect(client.getQueryData<typeof session>(MEMBER_SESSION_QUERY_KEY)?.accessToken).toBe(
    "renewed-2",
  );
});
