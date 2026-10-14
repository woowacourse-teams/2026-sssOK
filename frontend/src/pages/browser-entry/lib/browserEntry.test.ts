import { buildChromeIntentUrl, shouldOpenInChrome } from "./browserEntry";

describe("shouldOpenInChrome", () => {
  it.each([
    [
      "삼성 인터넷",
      "Mozilla/5.0 (Linux; Android 14; SM-S911N) AppleWebKit/537.36 Chrome/127.0 Mobile Safari/537.36 SamsungBrowser/26.0",
    ],
    [
      "Android WebView",
      "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A; wv) AppleWebKit/537.36 Version/4.0 Chrome/124.0 Mobile Safari/537.36",
    ],
    ["Android Firefox", "Mozilla/5.0 (Android 14; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0"],
  ])("%s에서는 Chrome 열기가 필요하다", (_, userAgent) => {
    expect(shouldOpenInChrome(userAgent)).toBe(true);
  });

  it.each([
    [
      "Android Chrome",
      "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36",
    ],
    ["iPhone", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)"],
    ["데스크톱", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0 Safari/537.36"],
  ])("%s에서는 기존 입장 경로를 사용한다", (_, userAgent) => {
    expect(shouldOpenInChrome(userAgent)).toBe(false);
  });
});

describe("buildChromeIntentUrl", () => {
  it("입장 주소와 쿼리, 실패 시 돌아올 공유 주소를 보존한다", () => {
    const target = new URL("https://sssok.app/rooms/ABCD1234?linkCode=483920");
    const fallback = new URL("https://sssok.app/join/ABCD1234?linkCode=483920");

    expect(buildChromeIntentUrl(target, fallback)).toBe(
      "intent://sssok.app/rooms/ABCD1234?linkCode=483920#Intent;scheme=https;" +
        "package=com.android.chrome;" +
        "S.browser_fallback_url=https%3A%2F%2Fsssok.app%2Fjoin%2FABCD1234%3FlinkCode%3D483920;end",
    );
  });
});
