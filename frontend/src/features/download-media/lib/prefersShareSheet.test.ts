import { prefersShareSheet } from "./prefersShareSheet";

const IPHONE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)";

const ANDROID_USER_AGENT = "Mozilla/5.0 (Linux; Android 15; Pixel 9)";

const DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

const IPAD_DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15)";

interface DeviceOptions {
  userAgent: string;
  fileShare: boolean;
  maxTouchPoints?: number;
}

const asDevice = ({ userAgent, fileShare, maxTouchPoints = 0 }: DeviceOptions) => {
  Object.defineProperty(navigator, "userAgent", {
    configurable: true,
    value: userAgent,
  });

  Object.defineProperty(navigator, "maxTouchPoints", {
    configurable: true,
    value: maxTouchPoints,
  });

  Object.defineProperty(navigator, "canShare", {
    configurable: true,
    value: fileShare ? () => true : undefined,
  });
};

describe("prefersShareSheet", () => {
  it("iPhone에서 파일 공유를 지원하면 참이다", () => {
    asDevice({
      userAgent: IPHONE_USER_AGENT,
      fileShare: true,
    });

    expect(prefersShareSheet()).toBe(true);
  });

  it("Android에서는 파일 공유를 지원해도 거짓이다", () => {
    asDevice({
      userAgent: ANDROID_USER_AGENT,
      fileShare: true,
    });

    expect(prefersShareSheet()).toBe(false);
  });

  it("데스크톱에서는 파일 공유를 지원해도 거짓이다", () => {
    asDevice({
      userAgent: DESKTOP_USER_AGENT,
      fileShare: true,
    });

    expect(prefersShareSheet()).toBe(false);
  });

  it("데스크톱 모드의 iPad에서는 파일 공유를 지원하면 참이다", () => {
    asDevice({
      userAgent: IPAD_DESKTOP_USER_AGENT,
      maxTouchPoints: 5,
      fileShare: true,
    });

    expect(prefersShareSheet()).toBe(true);
  });

  it("iPhone이라도 파일 공유를 지원하지 않으면 거짓이다", () => {
    asDevice({
      userAgent: IPHONE_USER_AGENT,
      fileShare: false,
    });

    expect(prefersShareSheet()).toBe(false);
  });
});
