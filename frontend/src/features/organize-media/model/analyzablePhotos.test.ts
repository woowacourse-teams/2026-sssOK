import type { GalleryItem, Media } from "@/entities/media";
import { countAnalyzablePhotos, organizeLimitOf } from "./analyzablePhotos";

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
