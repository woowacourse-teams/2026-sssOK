import { act, renderHook } from "@testing-library/react";

import { usePendingMedia } from "./usePendingMedia";

const file = new File(["photo"], "photo.jpg", { type: "image/jpeg" });

describe("usePendingMedia", () => {
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
