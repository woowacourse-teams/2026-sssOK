import { screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { MEMBER_SESSION_QUERY_KEY } from "@/entities/member-session";
import { server } from "@/mocks/server";
import { ROUTES } from "@/shared/config";
import { rememberLoginReturn } from "@/shared/lib";
import {
  GALLERY,
  STATE,
  attempt,
  authorizationUrl,
  completePath,
  deferred,
  installApi,
  interceptExternalNavigation,
  label,
  member,
  providers,
  renderCallback,
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

// QA: LG-07 (웹)
it("웹 인증 완료에 성공하면 회원 세션을 반영하고 방 목록으로 이동한다", async () => {
  for (const provider of providers) {
    sessionStorage.setItem(`sssok.oauth.${provider}`, JSON.stringify(attempt()));
    const app = renderCallback(provider);
    await screen.findByRole("heading", { name: "아직 들어간 방이 없어요" });
    expect(app.router.state.location.pathname).toBe(ROUTES.rooms);
    expect(app.client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toMatchObject(member());
    app.dispose();
  }
});

// QA: LG-08 (웹)
it("방에서 웹 인증을 마치고 돌아오면 원래 방 갤러리로 이동한다", async () => {
  rememberLoginReturn(GALLERY);
  sessionStorage.setItem("sssok.oauth.kakao", JSON.stringify(attempt()));
  const app = renderCallback();
  await waitFor(() => expect(app.router.state.location.pathname).toBe(GALLERY));
  expect(
    await screen.findByRole("heading", { name: "돌아갈 방 갤러리에 입장했어요." }),
  ).toBeInTheDocument();
  expect(sessionStorage.getItem("sssok.login-return")).toBeNull();
  app.dispose();
});

// QA: LG-11
it("완료 API 서버·네트워크 실패 → 콜백 오류·로그인 재시작 안내", async () => {
  for (const network of [false, true]) {
    let count = 0;
    server.use(
      http.post(completePath("kakao"), () => {
        count++;
        return network
          ? HttpResponse.error()
          : HttpResponse.json({ message: "완료 실패" }, { status: 500 });
      }),
    );
    sessionStorage.setItem("sssok.oauth.kakao", JSON.stringify(attempt()));
    const app = renderCallback();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      network ? "네트워크 연결을 확인해주세요." : "완료 실패",
    );
    expect(screen.getByRole("link", { name: "로그인 다시 시작하기" })).toHaveAttribute(
      "href",
      ROUTES.login,
    );
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(count).toBe(1);
    expect(app.client.getQueryData(MEMBER_SESSION_QUERY_KEY)).toBeFalsy();
    app.dispose();
  }
});

// QA: LG-16
it("완료 API 응답 대기 → 콜백 스피너·성공 후 이동", async () => {
  const response = deferred();
  server.use(
    http.post(completePath("kakao"), async () => {
      await response.promise;
      return HttpResponse.json({ data: member() });
    }),
  );
  sessionStorage.setItem("sssok.oauth.kakao", JSON.stringify(attempt()));
  const app = renderCallback();
  expect(screen.getByRole("status")).toHaveTextContent("로그인 완료 중");
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  response.resolve();
  await waitFor(() => expect(app.router.state.location.pathname).toBe(ROUTES.rooms));
  app.dispose();
});

// QA: LG-17
it("거절·만료·잘못된 인증 정보 → 실패 안내·완료 API 호출 안 함", async () => {
  for (const scenario of ["denied", "expired", "wrong-state", "missing-code"]) {
    sessionStorage.setItem(
      "sssok.oauth.kakao",
      JSON.stringify({
        ...attempt(),
        ...(scenario === "expired" && { expiresAt: "2020-01-01T00:00:00Z" }),
      }),
    );
    const query =
      scenario === "denied"
        ? `error=access_denied&state=${STATE}`
        : scenario === "wrong-state"
          ? "code=qa-code&state=wrong"
          : scenario === "missing-code"
            ? `state=${STATE}`
            : `code=qa-code&state=${STATE}`;
    const app = renderCallback("kakao", query);
    expect(await screen.findByRole("alert")).not.toBeEmptyDOMElement();
    expect(screen.getByRole("link", { name: "로그인 다시 시작하기" })).toBeInTheDocument();
    expect(calls).not.toContain(completePath("kakao"));
    app.dispose();
  }
});

// QA: LG-18
it("콜백 실패 후 로그인 다시 시작 → 로그인 화면·새 인증 요청", async () => {
  let count = 0;
  server.use(
    http.post(completePath("kakao"), () => {
      count++;
      return HttpResponse.json({ message: "완료 실패" }, { status: 500 });
    }),
  );
  sessionStorage.setItem("sssok.oauth.kakao", JSON.stringify(attempt()));
  const app = renderCallback();
  await app.user.click(await screen.findByRole("link", { name: "로그인 다시 시작하기" }));
  await app.user.click(await screen.findByRole("button", { name: label("kakao") }));
  await waitFor(() => expect(navigation).toHaveBeenCalledWith(authorizationUrl("kakao")));
  expect(count).toBe(1);
  app.dispose();
});
