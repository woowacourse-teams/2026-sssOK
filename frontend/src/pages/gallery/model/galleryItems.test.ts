import type { GalleryItem, MediaItem } from "@/entities/media";
import { countItemsByFolder, filterGalleryItems, mergeGalleryItems } from "./galleryItems";

const photo = (mediaId: number, folderIds: number[], uploaderId = 1): MediaItem => ({
  mediaId,
  folderIds,
  uploaderId,
  uploaderName: "사용자",
  type: "IMAGE",
  fileName: "photo.jpg",
  mimeType: "image/jpeg",
  size: 100,
  thumbnailUrl: "/thumbnail.jpg",
  displayUrl: "/original.jpg",
  width: 100,
  height: 100,
  duration: null,
  status: "READY",
  uploadedAt: "2026-09-28T00:00:00Z",
});

const preview = (mediaId: number, folderIds: number[]): GalleryItem => ({
  mediaId,
  folderIds,
  type: "local",
  file: new File(["photo"], "photo.jpg", { type: "image/jpeg" }),
});

describe("갤러리 목록에서 파생하는 개수", () => {
  it("서버 사진과 미리보기를 합쳐 전체와 폴더별 개수를 계산한다", () => {
    const items = mergeGalleryItems([photo(1, [10, 20])], [preview(2, [10]), preview(3, [])]);

    expect(items).toHaveLength(3);
    expect(countItemsByFolder(items)).toEqual(
      new Map([
        [10, 2],
        [20, 1],
      ]),
    );
  });

  it("미리보기가 SSE 서버 이미지로 바뀌고 재조회에 포함되어도 한 번만 센다", () => {
    const media = photo(1, [10]);
    const readySlot: GalleryItem = {
      type: "server",
      mediaId: 1,
      media,
      folderIds: media.folderIds,
    };

    for (const items of [
      mergeGalleryItems([], [preview(1, [10])]),
      mergeGalleryItems([], [readySlot]),
      mergeGalleryItems([media], [readySlot]),
    ]) {
      expect(items).toHaveLength(1);
      expect(countItemsByFolder(items).get(10)).toBe(1);
    }
  });

  it("재조회된 폴더 소속이 오래된 미리보기보다 우선한다", () => {
    const oldMedia = photo(1, [10]);
    const items = mergeGalleryItems(
      [photo(1, [20])],
      [
        {
          type: "server",
          mediaId: 1,
          media: oldMedia,
          folderIds: [10],
        },
      ],
    );

    expect(countItemsByFolder(items)).toEqual(new Map([[20, 1]]));
    expect(filterGalleryItems(items, 10, "all", 1)).toHaveLength(0);
    expect(filterGalleryItems(items, 20, "all", 1)).toHaveLength(1);
  });

  it("폴더·업로더 필터는 전체 및 폴더 개수의 원본을 바꾸지 않는다", () => {
    const items = mergeGalleryItems([photo(1, [10], 2)], [preview(2, [10])]);

    expect(filterGalleryItems(items, 10, "mine", 1).map((item) => item.mediaId)).toEqual([2]);
    expect(filterGalleryItems(items, 10, "others", 1).map((item) => item.mediaId)).toEqual([1]);
    expect(items).toHaveLength(2);
    expect(countItemsByFolder(items).get(10)).toBe(2);
  });

  it("화면에 표시하지 않는 빈 썸네일은 세지 않는다", () => {
    expect(mergeGalleryItems([{ ...photo(1, []), thumbnailUrl: " " }], [])).toHaveLength(0);
  });

  it("썸네일 생성 중에는 미리보기를 유지하고 서버의 최신 폴더 정보를 반영한다", () => {
    const items = mergeGalleryItems([{ ...photo(1, [20]), thumbnailUrl: "" }], [preview(1, [])]);

    expect(items).toEqual([{ ...preview(1, []), folderIds: [20] }]);
  });

  it("삭제가 원본 목록에 반영되면 전체와 폴더 개수도 줄어든다", () => {
    const remainingPhotos = [photo(1, [10]), photo(2, [10])].filter((item) => item.mediaId !== 1);
    const items = mergeGalleryItems(remainingPhotos, []);

    expect(items).toHaveLength(1);
    expect(countItemsByFolder(items).get(10)).toBe(1);
  });
});
