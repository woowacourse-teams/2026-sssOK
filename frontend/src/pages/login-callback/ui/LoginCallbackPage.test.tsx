import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { StrictMode } from "react";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { http, HttpResponse } from "msw";

import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";
import { captureLoginCallback } from "../api/completeLogin";
import { LoginCallbackPage } from "./LoginCallbackPage";

const state = "s".repeat(43);
const verifier = "v".repeat(43);
const member = () => ({
  accessToken: "member-access",
  userId: 123,
  nickname: "회원",
  memberStatus: "REGISTERED",
  expiresAt: new Date(Date.now() + 1800_000).toISOString(),
  refreshExpiresAt: "2099-01-01T00:00:00Z",
});

const setup = (provider = "kakao", query = `code=provider-code&state=${state}`) => {
  const path = `/app/auth/callback/${provider}`;
  history.replaceState(null, "", `${path}?${query}`);
  captureLoginCallback();
  const client = new QueryClient();
  const router = createMemoryRouter(
    [
      { path: "/app/auth/callback/:provider", element: <LoginCallbackPage /> },
      { path: ROUTES.rooms, element: <p>방 목록 도착</p> },
    ],
    { initialEntries: [path] },
  );
  render(
    <StrictMode>
      <QueryClientProvider client={client}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </StrictMode>,
  );
  return { client, router };
};

beforeEach(() => {
  for (const provider of ["kakao", "apple"])
    sessionStorage.setItem(
      `sssok.oauth.${provider}`,
      JSON.stringify({
        state,
        loginVerifier: verifier,
        expiresAt: new Date(Date.now() + 300_000).toISOString(),
      }),
    );
  localStorage.setItem("sssok.auth:OLDROOM", "anonymous-session");
  localStorage.setItem("setting", "keep");
});
afterEach(() => {
  sessionStorage.clear();
  localStorage.clear();
  history.replaceState(null, "", "/");
});

it.each(["kakao", "apple"])(
  "%s 콜백은 완료 API를 한 번 호출한 뒤 회원 세션을 저장한다",
  async (provider) => {
    let calls = 0;
    server.use(
      http.post(`${API_BASE_URL}/auth/oauth/${provider}/login`, async ({ request }) => {
        calls++;
        expect(request.credentials).toBe("include");
        expect(request.headers.get("X-CSRF-Protection")).toBe("1");
        expect(await request.json()).toEqual({
          code: "provider-code",
          state,
          loginVerifier: verifier,
          refreshTokenDelivery: "COOKIE",
        });
        expect(sessionStorage.getItem(`sssok.oauth.${provider}`)).toBeNull();
        return HttpResponse.json({ data: member() });
      }),
    );
    const { client, router } = setup(provider);
    expect(location.search).toBe("");
    expect(await screen.findByText("방 목록 도착")).toBeInTheDocument();
    expect(client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toMatchObject({
      accessToken: "member-access",
    });
    expect(localStorage.getItem("sssok.auth:OLDROOM")).toBeNull();
    expect(localStorage.getItem("setting")).toBe("keep");
    expect(JSON.stringify(localStorage)).not.toContain("member-access");
    expect(router.state.historyAction).toBe("REPLACE");
    expect(calls).toBe(1);
  },
);

it.each([
  `error=access_denied&state=${state}`,
  `code=code&state=${"x".repeat(43)}`,
  `code=code&code=duplicate&state=${state}`,
  `state=${state}`,
])("잘못된 콜백은 요청하지 않고 익명 세션도 유지한다: %s", async (query) => {
  let calls = 0;
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/kakao/login`, () => {
      calls++;
      return HttpResponse.json({ data: member() });
    }),
  );
  setup("kakao", query);
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(calls).toBe(0);
  expect(sessionStorage.getItem("sssok.oauth.kakao")).toBeNull();
  expect(localStorage.getItem("sssok.auth:OLDROOM")).toBe("anonymous-session");
});

it.each(["expired", "missing"])("%s 요청은 로그인부터 다시 시작하도록 안내한다", async (kind) => {
  if (kind === "expired")
    sessionStorage.setItem(
      "sssok.oauth.kakao",
      JSON.stringify({ state, loginVerifier: verifier, expiresAt: "2020-01-01T00:00:00Z" }),
    );
  else sessionStorage.removeItem("sssok.oauth.kakao");
  setup();
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "로그인 다시 시작하기" })).toHaveAttribute(
    "href",
    ROUTES.login,
  );
});

it.each([401, 500])("완료 API %s 실패는 재전송하지 않고 익명 세션을 유지한다", async (status) => {
  let calls = 0;
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/kakao/login`, () => {
      calls++;
      return HttpResponse.json({ message: "완료 실패" }, { status });
    }),
  );
  setup();
  expect(await screen.findByRole("alert")).toHaveTextContent("완료 실패");
  expect(localStorage.getItem("sssok.auth:OLDROOM")).toBe("anonymous-session");
  expect(calls).toBe(1);
});
