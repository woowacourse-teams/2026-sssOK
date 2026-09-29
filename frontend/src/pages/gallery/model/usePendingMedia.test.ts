import { act, renderHook } from "@testing-library/react";

import type { Media } from "@/entities/media";
import { usePendingMedia } from "./usePendingMedia";

const file = new File(["photo"], "photo.jpg", { type: "image/jpeg" });
const processingMedia: Media = {
  mediaId: 1,
  type: "IMAGE",
  fileName: "photo.jpg",
  mimeType: "image/jpeg",
  size: 100,
  thumbnailUrl: null,
  displayUrl: "https://example.com/display.jpg",
  width: null,
  height: null,
  duration: null,
  folderIds: [],
  uploaderId: 2,
  uploaderName: "다른 사용자",
  status: "PROCESSING",
  uploadedAt: "2026-09-29T00:00:00Z",
};

describe("usePendingMedia", () => {
  it("다른 사용자의 처리 중 미디어를 미리보기에 추가한다", () => {
    const { result } = renderHook(() => usePendingMedia());

    act(() => result.current.addProcessingMedia(processingMedia));

    expect(result.current.uploadSlots).toEqual([
      {
        mediaId: processingMedia.mediaId,
        type: "server",
        media: processingMedia,
        folderIds: processingMedia.folderIds,
      },
    ]);
  });

  it("같은 미디어의 로컬 미리보기가 있으면 처리 중 미디어로 덮지 않는다", () => {
    const { result } = renderHook(() => usePendingMedia());

    act(() => result.current.addPendingMedia({ mediaId: 1, file, folderIds: [] }));
    act(() => result.current.addProcessingMedia(processingMedia));

    expect(result.current.uploadSlots).toEqual([
      { mediaId: 1, type: "local", file, folderIds: [] },
    ]);
  });

  it("미리보기를 폴더에 담고 꺼낸 상태를 즉시 반영한다", () => {
    const { result } = renderHook(() => usePendingMedia());

    act(() => {
      result.current.addPendingMedia({ mediaId: 1, file, folderIds: [10] });
      result.current.addPendingMedia({ mediaId: 2, file, folderIds: [] });
    });

    act(() => result.current.addPendingMediaToFolder([1, 2], 20));
    expect(result.current.uploadSlots.map((item) => item.folderIds)).toEqual([[20], [10, 20]]);

    act(() => result.current.removePendingMediaFromFolder([1], 20));
    expect(result.current.uploadSlots.map((item) => item.folderIds)).toEqual([[20], [10]]);
  });
});
