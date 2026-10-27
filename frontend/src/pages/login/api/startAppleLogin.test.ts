import { http, HttpResponse } from "msw";

import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { startAppleLogin } from "./startAppleLogin";

const state = "s".repeat(43);
const response = () => ({
  authorizationUrl: `https://appleid.apple.com/auth/authorize?state=${state}`,
  state,
  loginVerifier: "v".repeat(43),
  expiresAt: new Date(Date.now() + 300_000).toISOString(),
});

afterEach(() => sessionStorage.clear());

it("익명 토큰 없이 시작 API를 호출하고 확인값을 보관한다", async () => {
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/apple/start`, async ({ request }) => {
      expect(request.method).toBe("POST");
      expect(request.credentials).toBe("omit");
      expect(request.headers.get("Authorization")).toBeNull();
      expect(await request.text()).toBe("");
      return HttpResponse.json({ data: response() });
    }),
  );
  await expect(startAppleLogin()).resolves.toBe(response().authorizationUrl);
  expect(JSON.parse(sessionStorage.getItem("sssok.oauth.apple")!)).toMatchObject({
    state,
    loginVerifier: "v".repeat(43),
  });
});

it("시작 실패 시 오류를 반환하고 이전 확인값을 남기지 않는다", async () => {
  sessionStorage.setItem("sssok.oauth.apple", "old");
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/apple/start`, () =>
      HttpResponse.json({ code: "UNAVAILABLE", message: "설정이 필요해요." }, { status: 503 }),
    ),
  );
  await expect(startAppleLogin()).rejects.toMatchObject({ status: 503 });
  expect(sessionStorage.getItem("sssok.oauth.apple")).toBeNull();
});

it("Apple가 아닌 주소에는 이동하지 않는다", async () => {
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/apple/start`, () =>
      HttpResponse.json({ data: { ...response(), authorizationUrl: "https://example.com" } }),
    ),
  );
  await expect(startAppleLogin()).rejects.toMatchObject({ code: "INVALID_RESPONSE" });
  expect(sessionStorage.getItem("sssok.oauth.apple")).toBeNull();
});
