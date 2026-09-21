import { describeUserAgent } from "./describeUserAgent";

describe("describeUserAgent", () => {
  it("iOS 사파리에서 기기와 브라우저 버전을 읽는다", () => {
    expect(
      describeUserAgent(
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1",
      ),
    ).toEqual({ device: "iPhone", browser: "Safari 17.4" });
  });

  it("안드로이드는 UA 에 적힌 모델 코드를 그대로 쓴다", () => {
    expect(
      describeUserAgent(
        "Mozilla/5.0 (Linux; Android 14; SM-S911N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36",
      ),
    ).toEqual({ device: "SM-S911N", browser: "Chrome 127" });
  });

  it("모델을 가린 안드로이드는 Android 로만 적는다", () => {
    expect(
      describeUserAgent(
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36",
      ).device,
    ).toBe("Android");
  });

  it("맥은 iPhone 의 'like Mac OS X' 와 헷갈리지 않는다", () => {
    expect(
      describeUserAgent(
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
      ),
    ).toEqual({ device: "Mac", browser: "Safari 17.5" });
  });

  it("카카오톡 인앱 브라우저를 사파리로 읽지 않는다", () => {
    expect(
      describeUserAgent(
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 10.4.0",
      ),
    ).toEqual({ device: "iPhone", browser: "KakaoTalk" });
  });

  it("엣지를 크롬으로 읽지 않는다", () => {
    expect(
      describeUserAgent(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 Edg/127.0.0.0",
      ),
    ).toEqual({ device: "Windows", browser: "Edge 127" });
  });

  it("헤더 없이 들어온 의견은 둘 다 비운다", () => {
    expect(describeUserAgent(null)).toEqual({ device: null, browser: null });
    expect(describeUserAgent("   ")).toEqual({ device: null, browser: null });
  });

  it("해석되지 않는 값이어도 터지지 않는다", () => {
    expect(describeUserAgent("curl/8.4.0")).toEqual({ device: null, browser: null });
  });
});
