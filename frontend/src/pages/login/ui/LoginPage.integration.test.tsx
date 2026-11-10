import { act, fireEvent, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { server } from "@/mocks/server";
import { API_BASE_URL, ROUTES } from "@/shared/config";
import { rememberLoginReturn } from "@/shared/lib";
import {
  GALLERY,
  STATE,
  attempt,
  authorizationUrl,
  deferred,
  enableNative,
  installApi,
  interceptExternalNavigation,
  label,
  member,
  openLogin,
  providers,
  renderApp,
  startPath,
  resetLoginTestState,
} from "../../../../test/loginIntegration";

let navigation: ReturnType<typeof interceptExternalNavigation>;
let calls: string[];
beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
  calls = installApi();
  navigation = interceptExternalNavigation();
});
afterEach(resetLoginTestState);

// QA: LG-20
it("로그인 화면 하단에서 이용약관을 누르면 이용약관 페이지가 열린다", async () => {
  const app = await openLogin();
  const footer = screen.getByRole("navigation", { name: "약관 및 정책" });

  await app.user.click(within(footer).getByRole("link", { name: "이용약관" }));

  expect(app.router.state.location.pathname).toBe(ROUTES.terms);
  expect(screen.getByRole("heading", { level: 1, name: "이용약관" })).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "제1조 목적과 정의" })).toBeInTheDocument();
  expect(navigation).not.toHaveBeenCalled();
  app.dispose();
});

// QA: LG-21
it("로그인 화면 하단에서 개인정보처리방침을 누르면 개인정보처리방침 페이지가 열린다", async () => {
  const app = await openLogin();
  const footer = screen.getByRole("navigation", { name: "약관 및 정책" });

  await app.user.click(within(footer).getByRole("link", { name: "개인정보처리방침" }));

  expect(app.router.state.location.pathname).toBe(ROUTES.privacy);
  expect(screen.getByRole("heading", { level: 1, name: "개인정보처리방침" })).toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "1. 처리하는 개인정보와 이용 목적" }),
  ).toBeInTheDocument();
  expect(navigation).not.toHaveBeenCalled();
  app.dispose();
});

// QA: LG-02
it("웹 카카오 로그인 → 시작 API·검증 후 카카오 인증 주소로 이동", async () => {
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  await waitFor(() => expect(navigation).toHaveBeenCalledWith(authorizationUrl("kakao")));
  expect(calls.filter((path) => path === startPath("kakao"))).toHaveLength(1);
  expect(JSON.parse(sessionStorage.getItem("sssok.oauth.kakao")!)).toMatchObject({ state: STATE });
  app.dispose();
});

// QA: LG-03
it("웹 Apple 로그인 → 시작 API·검증 후 Apple 인증 주소로 이동", async () => {
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("apple") }));
  await waitFor(() => expect(navigation).toHaveBeenCalledWith(authorizationUrl("apple")));
  expect(calls.filter((path) => path === startPath("apple"))).toHaveLength(1);
  expect(JSON.parse(sessionStorage.getItem("sssok.oauth.apple")!)).toMatchObject({ state: STATE });
  app.dispose();
});

// QA: LG-04
it("앱 소셜 로그인 → 선택한 제공자의 SDK 인증 요청", async () => {
  for (const provider of providers) {
    const sdk = enableNative();
    const app = await openLogin();
    await app.user.click(screen.getByRole("button", { name: label(provider) }));
    await waitFor(() => expect(sdk.requests).toHaveLength(1));
    expect(sdk.requests[0]).toMatchObject({ type: "auth/login", provider });
    if (provider === "apple") expect(sdk.requests[0].nonce).toBe(attempt().nonce);
    expect(navigation).not.toHaveBeenCalled();
    await sdk.respond("cancelled");
    app.dispose();
  }
});

// QA: LG-05
it("요청 대기 → 즉시 스피너·버튼 숨김·로그인 영역의 예약 공간 유지", async () => {
  const sdk = enableNative();
  const app = await openLogin();
  const button = screen.getByRole("button", { name: label("kakao") });
  const controls = button.parentElement!;
  const height = getComputedStyle(controls).minHeight;
  expect(height).toBe("120px");
  await app.user.click(button);
  const spinner = screen.getByRole("status");
  expect(spinner).toHaveTextContent("로그인 중이에요.");
  expect(spinner.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
  expect(spinner.parentElement).toBe(controls);
  expect(getComputedStyle(controls).minHeight).toBe(height);
  expect(
    screen.queryByRole("button", { name: /카카오 로그인|Apple로 로그인/ }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("navigation", { name: "약관 및 정책" })).toBeInTheDocument();
  await sdk.respond("cancelled");
  app.dispose();
});

// QA: LG-06
it("웹 인증 주소로 이동 시작 → 페이지를 떠날 때까지 스피너 유지", async () => {
  for (const provider of providers) {
    const app = await openLogin();
    await app.user.click(screen.getByRole("button", { name: label(provider) }));
    await waitFor(() => expect(navigation).toHaveBeenCalledWith(authorizationUrl(provider)));
    expect(screen.getByRole("status")).toHaveTextContent("로그인 중이에요.");
    expect(screen.queryByRole("button", { name: label(provider) })).not.toBeInTheDocument();
    app.dispose();
  }
});

// QA: LG-07 (앱)
it("앱 로그인에 성공하면 회원 세션을 반영하고 방 목록으로 이동한다", async () => {
  const sdk = enableNative();
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  await sdk.respond("success");
  await screen.findByRole("heading", { name: "아직 들어간 방이 없어요" });
  expect(app.router.state.location.pathname).toBe(ROUTES.rooms);
  expect(app.client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toMatchObject(member());
  app.dispose();
});

// QA: LG-08 (앱)
it("방에서 앱 로그인에 성공하면 원래 방 갤러리로 돌아간다", async () => {
  const sdk = enableNative();
  rememberLoginReturn(GALLERY);
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  await sdk.respond("success");
  await waitFor(() => expect(app.router.state.location.pathname).toBe(GALLERY));
  expect(
    await screen.findByRole("heading", { name: "돌아갈 방 갤러리에 입장했어요." }),
  ).toBeInTheDocument();
  expect(sessionStorage.getItem("sssok.login-return")).toBeNull();
  app.dispose();
});

// QA: LG-09
it("앱 인증 취소 → 로그인 유지·버튼 복원·재시도", async () => {
  const sdk = enableNative();
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  await sdk.respond("cancelled");
  expect(app.router.state.location.pathname).toBe(ROUTES.login);
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  expect(sdk.requests).toHaveLength(2);
  expect(calls).not.toContain("kakao:native-login");
  await sdk.respond("cancelled");
  app.dispose();
});

// QA: LG-10-1
it("시작 API 500 → 버튼 위 오류·스피너 종료·버튼 복원", async () => {
  server.use(
    http.post(startPath("kakao"), () =>
      HttpResponse.json({ message: "시작 실패" }, { status: 500 }),
    ),
  );
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  const error = await screen.findByRole("alert");
  expect(error).toHaveTextContent("시작 실패");
  const button = screen.getByRole("button", { name: label("kakao") });
  expect(button).toBeEnabled();
  expect(error.compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
  expect(navigation).not.toHaveBeenCalled();
  app.dispose();
});

// QA: LG-10-2
it("시작 API 네트워크 실패 → 연결 확인 문구·재시도 가능", async () => {
  server.use(http.post(startPath("kakao"), () => HttpResponse.error()));
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  expect(await screen.findByRole("alert")).toHaveTextContent("네트워크 연결을 확인해주세요.");
  expect(screen.getByRole("button", { name: label("kakao") })).toBeEnabled();
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
  expect(navigation).not.toHaveBeenCalled();
  app.dispose();
});

// QA: LG-12
it("같은 버튼 재시도 → 이전 오류 제거·새 로그인 요청", async () => {
  let count = 0;
  const response = deferred();
  server.use(
    http.post(startPath("kakao"), async () => {
      if (++count === 1) return HttpResponse.json({ message: "첫 요청 실패" }, { status: 500 });
      await response.promise;
      return HttpResponse.json({
        data: { ...attempt(), authorizationUrl: authorizationUrl("kakao") },
      });
    }),
  );
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  await screen.findByRole("alert");
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  expect(screen.getByRole("status")).toBeInTheDocument();
  response.resolve();
  await waitFor(() => expect(navigation).toHaveBeenCalledWith(authorizationUrl("kakao")));
  expect(count).toBe(2);
  app.dispose();
});

// QA: LG-13
it("다른 로그인 방식 선택 → 이전 오류 제거·선택한 SDK 요청", async () => {
  for (const provider of providers) {
    const sdk = enableNative();
    const app = await openLogin();
    await app.user.click(screen.getByRole("button", { name: label(provider) }));
    await waitFor(() => expect(sdk.requests).toHaveLength(1));
    await sdk.respond("error");
    await screen.findByRole("alert");
    const other = provider === "kakao" ? "apple" : "kakao";
    await app.user.click(screen.getByRole("button", { name: label(other) }));
    await waitFor(() => expect(sdk.requests).toHaveLength(2));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(sdk.requests[1].provider).toBe(other);
    await sdk.respond("cancelled");
    app.dispose();
  }
});

// QA: LG-14
it("앱 인증 실패·시간 만료 → 오류·버튼 복원", async () => {
  const sdk = enableNative();
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  await sdk.respond("error");
  expect(await screen.findByRole("alert")).toHaveTextContent("로그인을 완료하지 못했어요.");
  jest.useFakeTimers();
  fireEvent.click(screen.getByRole("button", { name: label("kakao") }));
  await act(async () => {
    jest.advanceTimersByTime(300_001);
  });
  expect(screen.getByRole("alert")).toHaveTextContent("로그인 시간이 만료됐어요.");
  expect(screen.getByRole("button", { name: label("kakao") })).toBeEnabled();
  app.dispose();
});

// QA: LG-15
it("외부 인증에서 돌아와 페이지 복원 → 버튼 복구·재로그인", async () => {
  const app = await openLogin();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  await waitFor(() => expect(navigation).toHaveBeenCalledTimes(1));
  act(() => window.dispatchEvent(new PageTransitionEvent("pageshow", { persisted: true })));
  expect(screen.queryByRole("status")).not.toBeInTheDocument();
  await app.user.click(screen.getByRole("button", { name: label("kakao") }));
  await waitFor(() => expect(navigation).toHaveBeenCalledTimes(2));
  app.dispose();
});

// QA: LG-19
it("로그인한 회원의 로그인 페이지 진입 → 확인 중 로딩 후 방 목록", async () => {
  const response = deferred();
  server.use(
    http.post(`${API_BASE_URL}/auth/refresh`, async () => {
      await response.promise;
      return HttpResponse.json({ data: member() });
    }),
  );
  const app = renderApp();
  expect(screen.getByRole("status")).toHaveTextContent("로그인 확인 중");
  expect(screen.queryByRole("button", { name: label("kakao") })).not.toBeInTheDocument();
  response.resolve();
  await waitFor(() => expect(app.router.state.location.pathname).toBe(ROUTES.rooms));
  expect(navigation).not.toHaveBeenCalled();
  app.dispose();
});
