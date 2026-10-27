import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse, delay } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";

import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { server } from "@/mocks/server";
import { LoginPage } from "@/pages/login";
import { API_BASE_URL, ROUTES } from "@/shared/config";
import { RedirectIfMember } from "./RedirectIfMember";
import { RequireMember } from "./RequireMember";

const member = () => ({
  accessToken: "member-access",
  userId: 123,
  nickname: "회원",
  expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
  refreshExpiresAt: "2099-01-01T00:00:00Z",
  memberStatus: "REGISTERED",
});

const setup = (path: string = ROUTES.rooms) => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      {
        element: <RequireMember />,
        children: [{ path: ROUTES.rooms, element: <p>회원 방 목록</p> }],
      },
      {
        element: <RedirectIfMember />,
        children: [{ path: ROUTES.login, element: <LoginPage /> }],
      },
    ],
    { initialEntries: [path] },
  );
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { router, client };
};

it("복원이 끝나기 전에는 방 목록이나 로그인 화면을 표시하지 않는다", async () => {
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, async ({ request }) => {
      expect(request.credentials).toBe("include");
      expect(request.headers.get("X-CSRF-Protection")).toBe("1");
      await delay(50);
      return HttpResponse.json({ data: member() });
    }),
  );
  setup();
  expect(screen.queryByText("회원 방 목록")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "카카오 로그인" })).not.toBeInTheDocument();
  expect(await screen.findByText("회원 방 목록")).toBeInTheDocument();
});

it.each(["401", "ANONYMOUS"])(
  "%s이면 익명 로컬 토큰과 무관하게 로그인으로 보낸다",
  async (kind) => {
    localStorage.setItem("sssok.auth:TESTROOM", JSON.stringify({ accessToken: "anonymous" }));
    server.use(
      http.post(`${API_BASE_URL}/auth/refresh`, () =>
        kind === "401"
          ? HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 })
          : HttpResponse.json({ data: { ...member(), memberStatus: "ANONYMOUS" } }),
      ),
    );
    const { router } = setup();
    expect(await screen.findByRole("button", { name: "카카오 로그인" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(ROUTES.login);
    expect(router.state.historyAction).toBe("REPLACE");
    expect(localStorage.getItem("sssok.auth:TESTROOM")).not.toBeNull();
    localStorage.clear();
  },
);

it.each([500, 403])(
  "복원 %s 오류는 로그인 이동 대신 재시도하고 성공하면 목록을 연다",
  async (status) => {
    let calls = 0;
    server.use(
      http.post(`${API_BASE_URL}/auth/refresh`, () => {
        calls++;
        return calls === 1
          ? HttpResponse.json({ message: "실패" }, { status })
          : HttpResponse.json({ data: member() });
      }),
    );
    const { router } = setup();
    expect(await screen.findByRole("alert")).toHaveTextContent("로그인 정보를 확인하지 못했어요.");
    expect(router.state.location.pathname).toBe(ROUTES.rooms);
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByText("회원 방 목록")).toBeInTheDocument();
    expect(calls).toBe(2);
  },
);

it("로그인 완료로 회원 세션이 저장되면 방 목록으로 이동한다", async () => {
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => HttpResponse.json({}, { status: 401 })),
  );
  const { client, router } = setup(ROUTES.login);
  await screen.findByRole("button", { name: "카카오 로그인" });
  client.setQueryData(MEMBER_SESSION_QUERY_KEY, member());
  await waitFor(() => expect(router.state.location.pathname).toBe(ROUTES.rooms));
  expect(router.state.historyAction).toBe("REPLACE");
});

it("복원 응답이 잘못되면 회원으로 통과시키지 않는다", async () => {
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () =>
      HttpResponse.json({
        data: { ...member(), expiresAt: "invalid" },
      }),
    ),
  );
  const { router } = setup();
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.queryByText("회원 방 목록")).not.toBeInTheDocument();
  expect(router.state.location.pathname).toBe(ROUTES.rooms);
});

it("네트워크 실패는 로그인으로 보내지 않는다", async () => {
  server.use(http.post(`${API_BASE_URL}/auth/refresh`, () => HttpResponse.error()));
  const { router } = setup();
  expect(await screen.findByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  expect(router.state.location.pathname).toBe(ROUTES.rooms);
});
