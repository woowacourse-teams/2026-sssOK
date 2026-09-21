import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { routes } from "@/app/routes";
import { type AdminSession, getAdminSession, saveAdminSession } from "@/entities/admin-session";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";

const sessionOf = (adminId: number, name: string, role: string): AdminSession => ({
  accessToken: "mock-admin-token",
  adminId,
  loginId: "whoever",
  name,
  role,
  expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
});

const SUPER_ADMIN = sessionOf(1, "마이찬", "SUPER_ADMIN");
const ADMIN = sessionOf(4, "해니", "ADMIN");

const renderPage = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [ROUTES.adminAccounts] });

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return router;
};

const respondWith = (status: number, body: Record<string, unknown>) => {
  server.use(http.get(`${API_BASE_URL}/admin/accounts`, () => HttpResponse.json(body, { status })));
};

const main = () => screen.getByRole("main");
const rows = () => within(main()).getAllByRole("row").slice(1);

describe("AdminAccountsPage", () => {
  beforeEach(() => localStorage.clear());

  it("계정을 이름·아이디·역할·생성일로 나열한다", async () => {
    saveAdminSession(ADMIN);
    renderPage();

    await within(main()).findByText("superadmin");

    expect(rows()).toHaveLength(4);

    const [first, second] = rows();
    expect(first).toHaveTextContent("마이찬");
    expect(first).toHaveTextContent("superadmin");
    expect(first).toHaveTextContent("슈퍼관리자");
    expect(first).toHaveTextContent("2026-09-17");
    expect(second).toHaveTextContent("현미밥");
    expect(second).toHaveTextContent("관리자");
  });

  it("제목 옆에 전체 계정 수를 보여준다", async () => {
    saveAdminSession(ADMIN);
    renderPage();

    const title = await screen.findByRole("heading", { name: "계정" });

    await waitFor(() => expect(title.parentElement).toHaveTextContent("4"));
  });

  it("로그인한 본인 행에만 나 표시를 붙인다", async () => {
    saveAdminSession(ADMIN);
    renderPage();

    await within(main()).findByText("superadmin");

    const me = rows().filter((row) => within(row).queryByText("나"));
    expect(me).toHaveLength(1);
    expect(me[0]).toHaveTextContent("해니");
  });

  it("슈퍼관리자에게는 계정 추가·수정·삭제 버튼을 보여준다", async () => {
    saveAdminSession(SUPER_ADMIN);
    renderPage();

    await within(main()).findByText("superadmin");

    expect(within(main()).getByRole("button", { name: "+ 계정 추가" })).toBeInTheDocument();
    expect(within(main()).getAllByRole("button", { name: /수정$/ })).toHaveLength(4);
    expect(within(main()).getAllByRole("button", { name: /삭제$/ })).toHaveLength(4);
  });

  it("일반 관리자에게는 목록만 보여주고 버튼은 숨긴다", async () => {
    saveAdminSession(ADMIN);
    renderPage();

    await within(main()).findByText("superadmin");

    expect(within(main()).queryByRole("button")).not.toBeInTheDocument();
  });

  it("모르는 역할로 로그인하면 버튼을 숨긴다", async () => {
    saveAdminSession(sessionOf(4, "해니", "AUDITOR"));
    renderPage();

    await within(main()).findByText("superadmin");

    expect(within(main()).queryByRole("button")).not.toBeInTheDocument();
  });

  it("401 을 받으면 세션을 지우고 로그인 화면으로 보낸다", async () => {
    respondWith(401, { code: "UNAUTHORIZED", message: "인증이 필요합니다" });
    saveAdminSession(ADMIN);
    const router = renderPage();

    await waitFor(() => expect(router.state.location.pathname).toBe(ROUTES.adminLogin));
    expect(getAdminSession()).toBeNull();
  });

  it("403 을 받으면 권한이 없다고 안내하고 재시도 버튼은 주지 않는다", async () => {
    respondWith(403, { code: "ADMIN_FORBIDDEN", message: "관리자만 이용할 수 있습니다" });
    saveAdminSession(ADMIN);
    renderPage();

    expect(await screen.findByText("이 계정으로는 계정 목록을 볼 수 없어요.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
  });

  it("서버 오류면 다시 시도할 수 있다", async () => {
    respondWith(500, { code: "INTERNAL_SERVER_ERROR", message: "서버 오류" });
    saveAdminSession(ADMIN);
    renderPage();

    expect(await screen.findByText("계정을 불러오지 못했어요.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });
});
