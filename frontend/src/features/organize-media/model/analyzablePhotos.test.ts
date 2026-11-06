import type { GalleryItem, Media } from "@/entities/media";
import { countAnalyzablePhotos, hasProcessingPhotos, organizeLimitOf } from "./analyzablePhotos";

const serverItem = (mediaId: number, media: Partial<Media>): GalleryItem => ({
  mediaId,
  type: "server",
  folderIds: [],
  media: {
    mediaId,
    type: "IMAGE",
    mimeType: "image/jpeg",
    status: "READY",
    ...media,
  } as Media,
});

describe("countAnalyzablePhotos", () => {
  it("서버처럼 동영상·GIF·처리 중·올리는 중인 사진은 빼고 센다", () => {
    const items: GalleryItem[] = [
      serverItem(1, {}),
      serverItem(2, {}),
      serverItem(3, { type: "VIDEO", mimeType: "video/mp4" }),
      serverItem(4, { mimeType: "image/gif" }),
      serverItem(5, { status: "PROCESSING" }),
      { mediaId: 6, type: "local", file: new File([], "a.jpg"), folderIds: [] },
    ];

    expect(countAnalyzablePhotos(items)).toBe(2);
  });
});

describe("hasProcessingPhotos", () => {
  const localItem = (mediaId: number, type: string): GalleryItem => ({
    mediaId,
    type: "local",
    file: new File([], "a", { type }),
    folderIds: [],
  });

  it("사진이 모두 READY 면 막지 않는다", () => {
    expect(hasProcessingPhotos([serverItem(1, {}), serverItem(2, {})])).toBe(false);
  });

  it("처리 중이거나 예약만 된 사진이 한 장이라도 있으면 막는다", () => {
    expect(hasProcessingPhotos([serverItem(1, {}), serverItem(2, { status: "PROCESSING" })])).toBe(
      true,
    );
    expect(hasProcessingPhotos([serverItem(1, {}), serverItem(2, { status: "RESERVED" })])).toBe(
      true,
    );
  });

  it("아직 올리는 중인 사진이 있으면 막는다", () => {
    expect(hasProcessingPhotos([serverItem(1, {}), localItem(2, "image/jpeg")])).toBe(true);
  });

  it("파일 타입이 비어 있으면 확장자로 사진인지 본다", () => {
    const iphone = (mediaId: number, name: string): GalleryItem => ({
      mediaId,
      type: "local",
      file: new File([], name, { type: "" }),
      folderIds: [],
    });

    expect(hasProcessingPhotos([iphone(1, "IMG_0001.HEIC")])).toBe(true);
    expect(hasProcessingPhotos([iphone(1, "IMG_0002.MOV")])).toBe(false);
  });

  it("처리에 실패한 사진은 기다리지 않는다", () => {
    expect(hasProcessingPhotos([serverItem(1, {}), serverItem(2, { status: "FAILED" })])).toBe(
      false,
    );
  });

  it("동영상·GIF 는 처리 중이어도 막지 않는다", () => {
    expect(
      hasProcessingPhotos([
        serverItem(1, {}),
        serverItem(2, { type: "VIDEO", mimeType: "video/mp4", status: "PROCESSING" }),
        serverItem(3, { mimeType: "image/gif", status: "PROCESSING" }),
        localItem(4, "video/mp4"),
        localItem(5, "image/gif"),
      ]),
    ).toBe(false);
  });
});

describe("organizeLimitOf", () => {
  it("1~200장이면 막지 않는다", () => {
    expect(organizeLimitOf(1)).toBeNull();
    expect(organizeLimitOf(200)).toBeNull();
  });

  it("200장을 넘으면 장수와 함께 막는다", () => {
    expect(organizeLimitOf(201)).toEqual({ reason: "tooMany", count: 201 });
  });

  it("사진이 없으면 막는다", () => {
    expect(organizeLimitOf(0)).toEqual({ reason: "empty" });
  });
});
