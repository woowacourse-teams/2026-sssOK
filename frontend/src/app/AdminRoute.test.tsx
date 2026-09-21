import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { type AdminSession, getAdminSession, saveAdminSession } from "@/entities/admin-session";
import { ROUTES } from "@/shared/config";
import { routes } from "./routes";

const sessionWith = (role: string): AdminSession => ({
  accessToken: "mock-admin-token",
  adminId: 4,
  loginId: "haeni",
  name: "해니",
  role,
  expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
});

const renderAt = (path: string) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });

  render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );

  return { router, queryClient };
};

const header = () => screen.getByRole("banner");

describe("관리자 헤더", () => {
  beforeEach(() => localStorage.clear());

  it("로그인한 관리자의 이름과 역할을 보여준다", () => {
    saveAdminSession(sessionWith("ADMIN"));
    renderAt(ROUTES.adminFeedbacks);

    expect(within(header()).getByText("해니")).toBeInTheDocument();
    expect(within(header()).getByText("관리자")).toBeInTheDocument();
  });

  it("슈퍼관리자는 슈퍼관리자 배지를 보여준다", () => {
    saveAdminSession(sessionWith("SUPER_ADMIN"));
    renderAt(ROUTES.adminFeedbacks);

    expect(within(header()).getByText("슈퍼관리자")).toBeInTheDocument();
  });

  it("모르는 역할은 관리자 배지로 보여준다", () => {
    saveAdminSession(sessionWith("AUDITOR"));
    renderAt(ROUTES.adminFeedbacks);

    expect(within(header()).queryByText("AUDITOR")).not.toBeInTheDocument();
    expect(within(header()).getByText("관리자")).toBeInTheDocument();
  });

  it("의견 화면에서는 의견 탭이 선택되어 있다", () => {
    saveAdminSession(sessionWith("ADMIN"));
    renderAt(ROUTES.adminFeedbacks);

    expect(within(header()).getByRole("link", { name: "의견" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("계정 탭을 누르면 계정 화면으로 가고 계정 탭이 선택된다", async () => {
    const user = userEvent.setup();
    saveAdminSession(sessionWith("ADMIN"));
    const { router } = renderAt(ROUTES.adminFeedbacks);

    await user.click(within(header()).getByRole("link", { name: "계정" }));

    expect(router.state.location.pathname).toBe(ROUTES.adminAccounts);
    expect(within(header()).getByRole("link", { name: "계정" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("로그아웃하면 세션을 지우고 로그인 화면으로 보낸다", async () => {
    const user = userEvent.setup();
    saveAdminSession(sessionWith("ADMIN"));
    const { router, queryClient } = renderAt(ROUTES.adminFeedbacks);

    await user.click(within(header()).getByRole("button", { name: "로그아웃" }));

    expect(router.state.location.pathname).toBe(ROUTES.adminLogin);
    expect(getAdminSession()).toBeNull();
    expect(queryClient.getQueryCache().findAll({ queryKey: ["admin"] })).toHaveLength(0);
  });

  it("로그아웃한 뒤 뒤로 가도 관리자 화면에 다시 들어갈 수 없다", async () => {
    const user = userEvent.setup();
    saveAdminSession(sessionWith("ADMIN"));
    const { router } = renderAt(ROUTES.adminFeedbacks);

    await user.click(within(header()).getByRole("button", { name: "로그아웃" }));
    await act(async () => {
      await router.navigate(-1);
    });

    await waitFor(() => expect(router.state.location.pathname).toBe(ROUTES.adminLogin));
    expect(screen.queryByRole("banner")).not.toBeInTheDocument();
  });

  it("로그인 화면에는 헤더가 없다", () => {
    renderAt(ROUTES.adminLogin);

    expect(screen.queryByRole("banner")).not.toBeInTheDocument();
  });
});
