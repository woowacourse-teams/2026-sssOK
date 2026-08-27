import { MAX_IMAGE_BYTES, MAX_VIDEO_BYTES, maxBytesOf, mediaKindOf } from "./mediaFileRules";

describe("mediaKindOf", () => {
  it.each(["cat.jpg", "cat.jpeg", "cat.png", "cat.gif"])("%s 는 이미지다", (fileName) => {
    expect(mediaKindOf(fileName)).toBe("IMAGE");
  });

  it.each(["trip.mp4", "trip.webm", "trip.mov"])("%s 는 영상이다", (fileName) => {
    expect(mediaKindOf(fileName)).toBe("VIDEO");
  });

  it("대문자 확장자도 같은 타입으로 본다", () => {
    expect(mediaKindOf("CAT.JPG")).toBe("IMAGE");
    expect(mediaKindOf("TRIP.MOV")).toBe("VIDEO");
  });

  it("점이 여러 개면 마지막 점 뒤를 확장자로 본다", () => {
    expect(mediaKindOf("2026.여름.휴가.png")).toBe("IMAGE");
  });

  it.each(["note.txt", "archive.zip", "noextension", "trailingdot."])(
    "%s 는 지원하지 않는다",
    (fileName) => {
      expect(mediaKindOf(fileName)).toBeNull();
    },
  );

  it("아이폰 기본 포맷인 heic 은 아직 지원하지 않는다", () => {
    expect(mediaKindOf("IMG_0001.HEIC")).toBeNull();
    expect(mediaKindOf("IMG_0001.heif")).toBeNull();
  });
});

describe("maxBytesOf", () => {
  it("사진은 10MB, 영상은 1GB 까지다", () => {
    expect(maxBytesOf("IMAGE")).toBe(MAX_IMAGE_BYTES);
    expect(maxBytesOf("VIDEO")).toBe(MAX_VIDEO_BYTES);
    expect(MAX_IMAGE_BYTES).toBe(10 * 1024 * 1024);
    expect(MAX_VIDEO_BYTES).toBe(1024 * 1024 * 1024);
  });
});
