import { requestNativeLogin, supportsNativeLogin } from "./nativeAuthBridge";

beforeEach(() => {
  window.ReactNativeWebView = { postMessage: jest.fn() };
  window.sssokApp = { auth: ["kakao", "apple"] };
});

afterEach(() => {
  delete window.ReactNativeWebView;
  delete window.sssokApp;
  jest.useRealTimers();
});

const reply = (detail: unknown) => window.dispatchEvent(new CustomEvent("sssok:auth", { detail }));
const sentRequest = () =>
  JSON.parse((window.ReactNativeWebView!.postMessage as jest.Mock).mock.calls[0][0]);

it("현재 요청과 제공자가 일치하는 결과만 수신한다", async () => {
  const pending = requestNativeLogin("kakao", new AbortController().signal);
  const request = sentRequest();
  reply({ ...request, requestId: "previous", status: "success", credential: "wrong" });
  reply({ ...request, provider: "apple", status: "success", credential: "wrong" });
  reply({ ...request, status: "success", credential: "token" });
  await expect(pending).resolves.toBe("token");
});

it("nonce만 앱으로 보내고 사용자 취소는 오류 없이 종료한다", async () => {
  const pending = requestNativeLogin("apple", new AbortController().signal, "nonce");
  expect(sentRequest()).toMatchObject({ provider: "apple", nonce: "nonce" });
  expect(sentRequest()).not.toHaveProperty("loginVerifier");
  reply({ ...sentRequest(), status: "cancelled" });
  await expect(pending).resolves.toBeNull();
});

it("페이지 이탈 시 요청을 종료한다", async () => {
  const controller = new AbortController();
  const pending = requestNativeLogin("kakao", controller.signal);
  controller.abort();
  await expect(pending).rejects.toMatchObject({ name: "AbortError" });
});

it("앱 응답이 없으면 대기를 종료한다", async () => {
  jest.useFakeTimers();
  const pending = requestNativeLogin("kakao", new AbortController().signal);
  jest.advanceTimersByTime(300_000);
  await expect(pending).rejects.toThrow("만료");
});

it("일반 브라우저·이전 앱·지원하지 않는 제공자는 웹 로그인 대상이다", () => {
  window.sssokApp = { download: 1 };
  expect(supportsNativeLogin("kakao")).toBe(false);
  window.sssokApp = { auth: ["kakao"] };
  expect(supportsNativeLogin("apple")).toBe(false);
  delete window.ReactNativeWebView;
  expect(supportsNativeLogin("kakao")).toBe(false);
});
