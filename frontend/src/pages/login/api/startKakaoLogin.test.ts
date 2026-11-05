import { http, HttpResponse } from "msw";

import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { startKakaoLogin } from "./startKakaoLogin";

const state = "s".repeat(43);
const response = () => ({
  authorizationUrl: `https://kauth.kakao.com/oauth/authorize?state=${state}`,
  state,
  loginVerifier: "v".repeat(43),
  expiresAt: new Date(Date.now() + 300_000).toISOString(),
});

const anonymous = (accessToken: string, expiresAt: string) =>
  JSON.stringify({ accessToken, userId: 1, nickname: "익명", expiresAt });

afterEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

it("익명 세션이 없으면 토큰 없이 시작 API를 호출하고 확인값을 보관한다", async () => {
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/kakao/start`, async ({ request }) => {
      expect(request.method).toBe("POST");
      expect(request.credentials).toBe("omit");
      expect(request.headers.get("Authorization")).toBeNull();
      expect(await request.text()).toBe("");
      return HttpResponse.json({ data: response() });
    }),
  );
  await expect(startKakaoLogin()).resolves.toBe(response().authorizationUrl);
  expect(JSON.parse(sessionStorage.getItem("sssok.oauth.kakao")!)).toMatchObject({
    state,
    loginVerifier: "v".repeat(43),
  });
});

it("시작 실패 시 오류를 반환하고 이전 확인값을 남기지 않는다", async () => {
  sessionStorage.setItem("sssok.oauth.kakao", "old");
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/kakao/start`, () =>
      HttpResponse.json({ code: "UNAVAILABLE", message: "설정이 필요해요." }, { status: 503 }),
    ),
  );
  await expect(startKakaoLogin()).rejects.toMatchObject({ status: 503 });
  expect(sessionStorage.getItem("sssok.oauth.kakao")).toBeNull();
});

it("카카오가 아닌 주소에는 이동하지 않는다", async () => {
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/kakao/start`, () =>
      HttpResponse.json({ data: { ...response(), authorizationUrl: "https://example.com" } }),
    ),
  );
  await expect(startKakaoLogin()).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  expect(sessionStorage.getItem("sssok.oauth.kakao")).toBeNull();
});

it("이 기기의 만료되지 않은 익명 토큰을 함께 보내 익명 활동을 합친다", async () => {
  localStorage.setItem("sssok.auth:ROOM1", anonymous("token-1", "2099-01-01T00:00:00Z"));
  localStorage.setItem("sssok.auth:ROOM2", anonymous("token-2", "2099-01-01T00:00:00Z"));
  localStorage.setItem("sssok.auth:OLD", anonymous("expired", "2000-01-01T00:00:00Z"));
  let body: unknown;
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/kakao/start`, async ({ request }) => {
      expect(request.headers.get("Authorization")).toBeNull();
      body = await request.json();
      return HttpResponse.json({ data: response() });
    }),
  );

  await expect(startKakaoLogin()).resolves.toBe(response().authorizationUrl);
  expect((body as { anonymousAccessTokens: string[] }).anonymousAccessTokens.sort()).toEqual([
    "token-1",
    "token-2",
  ]);
});

it("익명 토큰 때문에 거절되면 토큰 없이 다시 시작해 로그인은 이어 간다", async () => {
  localStorage.setItem("sssok.auth:ROOM1", anonymous("revoked", "2099-01-01T00:00:00Z"));
  const bodies: string[] = [];
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/kakao/start`, async ({ request }) => {
      const text = await request.text();
      bodies.push(text);
      return text
        ? HttpResponse.json({ code: "UNAUTHORIZED", message: "" }, { status: 401 })
        : HttpResponse.json({ data: response() });
    }),
  );

  await expect(startKakaoLogin()).resolves.toBe(response().authorizationUrl);
  expect(bodies).toEqual([JSON.stringify({ anonymousAccessTokens: ["revoked"] }), ""]);
});

it("서버 오류(5xx)는 토큰을 빼고 다시 보내지 않는다", async () => {
  localStorage.setItem("sssok.auth:ROOM1", anonymous("token-1", "2099-01-01T00:00:00Z"));
  let calls = 0;
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/kakao/start`, () => {
      calls++;
      return HttpResponse.json({ code: "UNAVAILABLE", message: "" }, { status: 503 });
    }),
  );

  await expect(startKakaoLogin()).rejects.toMatchObject({ status: 503 });
  expect(calls).toBe(1);
});
