import { API_BASE_URL } from "@/shared/config";
import { mediaAssetUrl } from "./mediaAssetUrl";

describe("mediaAssetUrl", () => {
  it("서버가 주는 상대 경로를 API 오리진 기준으로 푼다", () => {
    const { origin } = new URL(API_BASE_URL);

    expect(mediaAssetUrl("/api/v1/rooms/20/media/128/thumbnail")).toBe(
      `${origin}/api/v1/rooms/20/media/128/thumbnail`,
    );
  });

  // 페이지 오리진(개발 서버)으로 풀리면 index.html 이 200 으로 돌아와 조용히 깨진다.
  it("페이지 오리진으로 풀지 않는다", () => {
    expect(mediaAssetUrl("/api/v1/rooms/20/media/128/thumbnail")).not.toContain(
      window.location.origin,
    );
  });

  it("이미 절대 주소면 그대로 둔다", () => {
    const signed = "https://storage.example.com/thumb.png?sig=abc";

    expect(mediaAssetUrl(signed)).toBe(signed);
  });
});
