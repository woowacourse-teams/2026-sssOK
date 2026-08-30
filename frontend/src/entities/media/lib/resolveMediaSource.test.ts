import { resolveMediaSource } from "./resolveMediaSource";

const base = "https://api.ssssok.com/api/v1";

describe("resolveMediaSource", () => {
  it.each(["/rooms/16/media/5012/thumbnail", "rooms/16/media/5012/thumbnail"])(
    "상대 경로 %s에 API 베이스를 붙인다",
    (path) => {
      expect(resolveMediaSource(path, base)).toBe(`${base}/rooms/16/media/5012/thumbnail`);
    },
  );

  it("이미 API 접두사가 있는 경로를 중복해서 붙이지 않는다", () => {
    expect(resolveMediaSource("/api/v1/rooms/16/media/5012/original", `${base}/`)).toBe(
      `${base}/rooms/16/media/5012/original`,
    );
  });

  it("MSW의 상대 베이스 URL도 현재 origin으로 해석한다", () => {
    expect(resolveMediaSource("/rooms/16/media/5012/thumbnail", "/api/v1")).toBe(
      `${window.location.origin}/api/v1/rooms/16/media/5012/thumbnail`,
    );
  });

  it.each([
    `${base}/rooms/16/media/5012/original`,
    "https://cdn.example.com/5012.jpg",
    "//cdn.example.com/5012.jpg",
    "blob:http://localhost:3000/abc",
    "data:image/png;base64,abc",
  ])("완성된 URL은 그대로 유지한다: %s", (url) => {
    expect(resolveMediaSource(url, base)).toBe(url);
  });

  it("null은 이미지 요청을 만들지 않는다", () => {
    expect(resolveMediaSource(null, base)).toBeUndefined();
  });
});
