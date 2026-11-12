import { http, HttpResponse } from "msw";
import { server } from "@/mocks/server";
import { API_BASE_URL } from "@/shared/config";
import { requestNativeLogin } from "./nativeAuthBridge";
import { loginNative } from "./loginNative";

jest.mock("./nativeAuthBridge", () => ({ requestNativeLogin: jest.fn() }));
const sdk = jest.mocked(requestNativeLogin);
const session = (): import("@/entities/member-session").MemberSession => ({
  memberStatus: "REGISTERED",
  userId: 1,
  nickname: "회원",
  refreshExpiresAt: "2099-01-01T00:00:00Z",
  accessToken: "service-token",
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
});
const attempt = (): { state: string; loginVerifier: string; nonce: string; expiresAt: string } => ({
  state: "s".repeat(43),
  loginVerifier: "v".repeat(43),
  nonce: "n".repeat(43),
  expiresAt: new Date(Date.now() + 300_000).toISOString(),
});

it("카카오 SDK 토큰으로 웹뷰에서 COOKIE 로그인한다", async () => {
  sdk.mockResolvedValueOnce("kakao-token");
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/kakao/native/login`, async ({ request }) => {
      expect(request.credentials).toBe("include");
      expect(request.headers.get("X-CSRF-Protection")).toBe("1");
      expect(await request.json()).toEqual({
        kakaoAccessToken: "kakao-token",
        refreshTokenDelivery: "COOKIE",
      });
      return HttpResponse.json({ data: session() });
    }),
  );
  await expect(loginNative("kakao", new AbortController().signal)).resolves.toMatchObject({
    userId: 1,
    nickname: "회원",
    refreshExpiresAt: "2099-01-01T00:00:00Z",
  });
});

it("애플은 준비 후 nonce를 그대로 SDK에 전달하고 확인값과 코드를 완료 API에 보낸다", async () => {
  const signal = new AbortController().signal;
  const prepare = attempt();
  sdk.mockResolvedValueOnce("apple-code");
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/apple/native/start`, () =>
      HttpResponse.json({ data: prepare }),
    ),
    http.post(`${API_BASE_URL}/auth/oauth/apple/native/login`, async ({ request }) => {
      expect(request.credentials).toBe("include");
      expect(request.headers.get("X-CSRF-Protection")).toBe("1");
      expect(await request.json()).toEqual({
        code: "apple-code",
        state: prepare.state,
        loginVerifier: prepare.loginVerifier,
        refreshTokenDelivery: "COOKIE",
      });
      return HttpResponse.json({ data: session() });
    }),
  );
  await loginNative("apple", signal);
  expect(sdk).toHaveBeenCalledWith("apple", signal, prepare.nonce);
});

it("취소 시 서버 로그인 요청을 보내지 않는다", async () => {
  sdk.mockResolvedValueOnce(null);
  await expect(loginNative("kakao", new AbortController().signal)).resolves.toBeNull();
});

it("애플 코드 교환 실패는 자동으로 재시도하지 않는다", async () => {
  sdk.mockResolvedValueOnce("one-use-code");
  let calls = 0;
  server.use(
    http.post(`${API_BASE_URL}/auth/oauth/apple/native/start`, () =>
      HttpResponse.json({ data: attempt() }),
    ),
    http.post(`${API_BASE_URL}/auth/oauth/apple/native/login`, () => {
      calls++;
      return HttpResponse.json({ code: "INVALID_SOCIAL_AUTH" }, { status: 401 });
    }),
  );
  await expect(loginNative("apple", new AbortController().signal)).rejects.toMatchObject({
    status: 401,
  });
  expect(calls).toBe(1);
});
