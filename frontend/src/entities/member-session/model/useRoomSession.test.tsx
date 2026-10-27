import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import type { ReactNode } from "react";

import { saveRoomSession } from "@/entities/session";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { MEMBER_SESSION_QUERY_KEY } from "./useMemberSessionQuery";
import { useRoomSession } from "./useRoomSession";

const anonymous = {
  accessToken: "anonymous",
  userId: 1,
  nickname: "익명",
  expiresAt: "2099-01-01T00:00:00Z",
};
const member = {
  ...anonymous,
  accessToken: "member",
  userId: 2,
  memberStatus: "REGISTERED",
  refreshExpiresAt: anonymous.expiresAt,
};
const wrapper = (client: QueryClient) =>
  function Provider({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
afterEach(() => localStorage.clear());

it("기존 익명 세션보다 회원의 메모리 세션을 우선한다", () => {
  saveRoomSession("ROOMCODE", anonymous);
  const client = new QueryClient();
  client.setQueryData(MEMBER_SESSION_QUERY_KEY, member);
  const { result } = renderHook(() => useRoomSession("ROOMCODE"), { wrapper: wrapper(client) });
  expect(result.current.session?.accessToken).toBe("member");
});

it("새로고침 후 쿠키로 복원한 회원 세션을 방에서도 사용한다", async () => {
  server.use(http.post(`${API_BASE_URL}/auth/refresh`, () => HttpResponse.json({ data: member })));
  const { result } = renderHook(() => useRoomSession("ROOMCODE"), {
    wrapper: wrapper(new QueryClient()),
  });
  expect(result.current.session).toBeNull();
  await waitFor(() => expect(result.current.session?.accessToken).toBe("member"));
  expect(localStorage.length).toBe(0);
});

it("복원 실패 시 남아 있는 익명 세션으로 전환하지 않는다", async () => {
  saveRoomSession("ROOMCODE", anonymous);
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => new HttpResponse(null, { status: 500 })),
  );
  const { result } = renderHook(() => useRoomSession("ROOMCODE"), {
    wrapper: wrapper(new QueryClient()),
  });
  await waitFor(() => expect(result.current.isError).toBe(true));
  expect(result.current.session).toBeNull();
});
