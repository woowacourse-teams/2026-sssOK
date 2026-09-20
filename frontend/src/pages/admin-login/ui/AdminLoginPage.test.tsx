import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider, createMemoryRouter } from "react-router-dom";

import { getAdminSession, saveAdminSession } from "@/entities/admin-session";
import { ADMIN_CREDENTIALS, lockAdminLogin } from "@/mocks/handlers/admin";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";
import { routes } from "@/app/routes";

/** 목 핸들러가 받아들이는 계정을 그대로 가져온다 — 여기 다시 적으면 둘이 어긋난다. */
const VALID = ADMIN_CREDENTIALS;

const renderAt = (path: string = ROUTES.adminLogin) => {
  const router = createMemoryRouter(routes, { initialEntries: [path] });

  render(<RouterProvider router={router} />);

  return router;
};

const fillAndSubmit = async (
  user: ReturnType<typeof userEvent.setup>,
  { loginId, password }: { loginId: string; password: string },
) => {
  await user.type(screen.getByLabelText("아이디"), loginId);
  await user.type(screen.getByLabelText("비밀번호"), password);
  await user.click(screen.getByRole("button", { name: "로그인" }));
};

describe("AdminLoginPage", () => {
  beforeEach(() => localStorage.clear());

  it("기본 상태를 디자인대로 보여준다", () => {
    renderAt();

    expect(screen.getByText("관리자")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "쏙 관리자 로그인" })).toBeInTheDocument();
    expect(screen.getByText("발급받은 관리자 계정으로 로그인해주세요.")).toBeInTheDocument();
    expect(screen.getByLabelText("아이디")).toBeInTheDocument();
    expect(screen.getByLabelText("비밀번호")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그인" })).toBeInTheDocument();
  });

  it("비밀번호는 가려서 입력받는다", () => {
    renderAt();

    expect(screen.getByLabelText("비밀번호")).toHaveAttribute("type", "password");
  });

  it("아이디·비밀번호 중 하나라도 비어 있으면 버튼을 누를 수 없다", async () => {
    const user = userEvent.setup();
    renderAt();

    const button = screen.getByRole("button", { name: "로그인" });

    expect(button).toBeDisabled();

    await user.type(screen.getByLabelText("아이디"), VALID.loginId);
    expect(button).toBeDisabled();

    await user.type(screen.getByLabelText("비밀번호"), VALID.password);
    expect(button).toBeEnabled();
  });

  it("빈 채로는 서버에 요청하지 않는다", async () => {
    const user = userEvent.setup();
    let called = false;

    server.use(
      http.post(`${API_BASE_URL}/admin/auth/login`, () => {
        called = true;
        return HttpResponse.json({ code: "INVALID_REQUEST_BODY", message: "" }, { status: 400 });
      }),
    );
    renderAt();

    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(called).toBe(false);
  });

  it("로그인에 성공하면 세션을 저장하고 의견 화면으로 이동한다", async () => {
    const user = userEvent.setup();
    const router = renderAt();

    await fillAndSubmit(user, VALID);

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(ROUTES.adminFeedbacks);
    });

    const session = getAdminSession();

    expect(session?.accessToken).toBe("mock-admin-token");
    expect(session?.loginId).toBe(VALID.loginId);
    expect(session?.name).toBe(VALID.name);
    // 비밀번호는 어디에도 남지 않는다.
    expect(JSON.stringify(session)).not.toContain(VALID.password);
  });

  it("401 이면 서버 문구를 비밀번호 아래에 보여주고 아이디는 지우지 않는다", async () => {
    const user = userEvent.setup();
    renderAt();

    await fillAndSubmit(user, { loginId: VALID.loginId, password: "틀린비밀번호" });

    expect(await screen.findByText("아이디 또는 비밀번호가 올바르지 않습니다")).toBeInTheDocument();
    expect(screen.getByLabelText("비밀번호")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("아이디")).toHaveValue(VALID.loginId);
    expect(getAdminSession()).toBeNull();
  });

  it("429 면 Retry-After 만큼 버튼을 잠그고 매초 줄이다 다시 열어준다", async () => {
    const user = userEvent.setup();

    server.use(
      http.post(`${API_BASE_URL}/admin/auth/login`, () =>
        HttpResponse.json(
          { code: "ADMIN_LOGIN_RATE_LIMITED", message: "로그인 시도가 너무 많습니다" },
          { status: 429, headers: { "Retry-After": "1" } },
        ),
      ),
    );
    renderAt();

    // 응답을 받기까지는 진짜 타이머로 둔다 — 가짜 타이머 위에서는 요청이 끝나지 않는다.
    await fillAndSubmit(user, VALID);

    expect(
      await screen.findByRole("button", { name: "1초 후 다시 시도할 수 있어요" }),
    ).toBeDisabled();
    expect(
      screen.getByText(
        "로그인 실패가 여러 번 반복돼 잠시 막았어요. 시간이 지나면 다시 시도할 수 있어요.",
      ),
    ).toBeInTheDocument();

    // 초 단위로 줄어드는 동작 자체는 useRetryCountdown.test.ts 가 가짜 타이머로 검증한다.
    // 여기서는 잠금이 실제로 풀려 버튼이 되살아나는지만 본다.
    await waitFor(() => expect(screen.getByRole("button", { name: "로그인" })).toBeEnabled(), {
      timeout: 4000,
    });

    // 카운트다운이 끝나면 안내 문구도 사라진다.
    expect(
      screen.queryByText(/로그인 실패가 여러 번 반복돼 잠시 막았어요/),
    ).not.toBeInTheDocument();
  });

  it("Retry-After 가 숫자가 아니면 카운트다운 없이 다시 누를 수 있게 둔다", async () => {
    const user = userEvent.setup();

    server.use(
      http.post(`${API_BASE_URL}/admin/auth/login`, () =>
        HttpResponse.json(
          { code: "ADMIN_LOGIN_RATE_LIMITED", message: "로그인 시도가 너무 많습니다" },
          { status: 429, headers: { "Retry-After": "잠시 후" } },
        ),
      ),
    );
    renderAt();

    await fillAndSubmit(user, VALID);

    // 영구 잠김을 만들지 않는다 — 버튼 이름이 카운트다운으로 바뀌지도, 비활성으로 굳지도 않는다.
    await waitFor(() => expect(screen.getByRole("button", { name: "로그인" })).toBeEnabled());
  });

  it("네트워크가 끊겨도 버튼이 로딩 상태에 갇히지 않는다", async () => {
    const user = userEvent.setup();

    server.use(http.post(`${API_BASE_URL}/admin/auth/login`, () => HttpResponse.error()));
    renderAt();

    await fillAndSubmit(user, VALID);

    await waitFor(() => expect(screen.getByRole("button", { name: "로그인" })).toBeEnabled());
    // apiClient 가 fetch 실패를 앱 공통 문구로 바꿔 준다 — 화면마다 다른 말을 만들지 않는다.
    expect(screen.getByText("네트워크 연결을 확인해주세요.")).toBeInTheDocument();
  });

  it("목 핸들러도 실패가 쌓이면 429 로 잠근다", async () => {
    const user = userEvent.setup();
    lockAdminLogin(30);
    renderAt();

    await fillAndSubmit(user, VALID);

    expect(
      await screen.findByRole("button", { name: /초 후 다시 시도할 수 있어요/ }),
    ).toBeDisabled();
  });
});

describe("관리자 화면 접근 가드", () => {
  beforeEach(() => localStorage.clear());

  it("세션이 없으면 로그인 화면으로 보낸다", async () => {
    const router = renderAt(ROUTES.adminFeedbacks);

    await waitFor(() => expect(router.state.location.pathname).toBe(ROUTES.adminLogin));
  });

  it("만료된 세션도 없는 것으로 본다", async () => {
    saveAdminSession({
      accessToken: "낡은 토큰",
      adminId: 2,
      loginId: "hyeonmibap",
      name: "현미밥",
      role: "ADMIN",
      expiresAt: "2000-01-01T00:00:00Z",
    });

    const router = renderAt(ROUTES.adminFeedbacks);

    await waitFor(() => expect(router.state.location.pathname).toBe(ROUTES.adminLogin));
  });

  it("살아 있는 세션이면 통과시킨다", () => {
    saveAdminSession({
      accessToken: "mock-admin-token",
      adminId: 2,
      loginId: "hyeonmibap",
      name: "현미밥",
      role: "ADMIN",
      expiresAt: "9999-12-31T23:59:59Z",
    });

    const router = renderAt(ROUTES.adminFeedbacks);

    expect(router.state.location.pathname).toBe(ROUTES.adminFeedbacks);
  });
});
