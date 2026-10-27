import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router-dom";

import { server } from "@/mocks/server";
import { LoginPage } from "@/pages/login";
import { API_BASE_URL, ROUTES } from "@/shared/config";
import { RedirectIfMember } from "./RedirectIfMember";

const renderLogin = () => {
  const client = new QueryClient();
  const router = createMemoryRouter(
    [
      { element: <RedirectIfMember />, children: [{ path: ROUTES.login, element: <LoginPage /> }] },
      { path: ROUTES.rooms, element: <p>방 목록</p> },
    ],
    { initialEntries: [ROUTES.login] },
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
  render(<LoginPage />);
  expect(screen.getByRole("button", { name: "카카오 로그인" })).toBeInTheDocument();
});
