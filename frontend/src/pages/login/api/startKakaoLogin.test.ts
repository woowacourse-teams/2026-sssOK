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

afterEach(() => sessionStorage.clear());

it("익명 토큰 없이 시작 API를 호출하고 확인값을 보관한다", async () => {
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
