import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, MemoryRouter, RouterProvider } from "react-router-dom";

import { server } from "@/mocks/server";
import { LoginPage } from "@/pages/login";
import { API_BASE_URL, ROUTES } from "@/shared/config";
import { RedirectIfMember } from "./RedirectIfMember";

import { RequireAppRoomMember } from "./RequireMember";

const renderLogin = (path: string = ROUTES.login) => {
  const client = new QueryClient();
  const router = createMemoryRouter(
    [
      { element: <RedirectIfMember />, children: [{ path: ROUTES.login, element: <LoginPage /> }] },
      {
        element: <RequireAppRoomMember />,
        children: [{ path: "/rooms/:code", element: <p>초대받은 방</p> }],
      },
      { path: ROUTES.rooms, element: <p>방 목록</p> },
    ],
    { initialEntries: [path] },
  );
  render(
    <QueryClientProvider client={client}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
};

it("회원 복원 중에는 로그인 UI를 숨기고, 성공하면 방 목록으로 교체한다", async () => {
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, async () => {
      await delay(30);
      return HttpResponse.json({
        data: {
          accessToken: "member-token",
          memberStatus: "REGISTERED",
          userId: 1,
          nickname: "회원",
          expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
        },
      });
    }),
  );
  const router = renderLogin();
  expect(screen.queryByRole("button", { name: "카카오 로그인" })).not.toBeInTheDocument();
  expect(await screen.findByText("방 목록")).toBeInTheDocument();
  expect(router.state.historyAction).toBe("REPLACE");
});

it.each([401, 503])("세션 복원 %s 응답에서는 로그인 UI를 이용할 수 있다", async (status) => {
  server.use(http.post(`${API_BASE_URL}/auth/refresh`, () => HttpResponse.json({}, { status })));
  const router = renderLogin();
  expect(await screen.findByRole("button", { name: "카카오 로그인" })).toBeInTheDocument();
  expect(router.state.location.pathname).toBe(ROUTES.login);
});

it("LoginPage 자체는 세션 요청 없이 UI만 표시한다", () => {
  const client = new QueryClient();
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  expect(screen.getByRole("button", { name: "카카오 로그인" })).toBeInTheDocument();
  expect(client.getQueryCache().getAll()).toHaveLength(0);
});

it.each([
  [ROUTES.login, "방 목록"],
  ["/rooms/ABCDEF23?linkCode=483920#invite", "초대받은 방"],
])("앱 SDK 인증 후 진입 경로 %s에 맞게 복귀한다", async (path, expected) => {
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, () => HttpResponse.json({}, { status: 401 })),
    http.post(`${API_BASE_URL}/auth/oauth/kakao/native/login`, () =>
      HttpResponse.json({
        data: {
          accessToken: "member-token",
          memberStatus: "REGISTERED",
          userId: 1,
          nickname: "회원",
          expiresAt: new Date(Date.now() + 30 * 60_000).toISOString(),
        },
      }),
    ),
  );
  window.sssokApp = { auth: ["kakao"] };
  window.ReactNativeWebView = {
    postMessage: (raw) => {
      const request = JSON.parse(raw);
      window.dispatchEvent(
        new CustomEvent("sssok:auth", {
          detail: {
            requestId: request.requestId,
            provider: "kakao",
            status: "success",
            credential: "kakao-token",
          },
        }),
      );
    },
  };
  try {
    const router = renderLogin(path);
    await userEvent.click(await screen.findByRole("button", { name: "카카오 로그인" }));
    expect(await screen.findByText(expected)).toBeInTheDocument();
    if (path !== ROUTES.login)
      expect(router.state.location.search + router.state.location.hash).toBe(
        "?linkCode=483920#invite",
      );
    expect(router.state.historyAction).toBe("REPLACE");
  } finally {
    delete window.ReactNativeWebView;
    delete window.sssokApp;
  }
});

it("일반 웹 초대는 로그인 없이 방 입장 화면을 연다", async () => {
  renderLogin("/rooms/ABCDEF23");
  expect(await screen.findByText("초대받은 방")).toBeInTheDocument();
});
