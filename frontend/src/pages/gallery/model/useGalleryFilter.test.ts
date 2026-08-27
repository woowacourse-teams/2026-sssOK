import { act, renderHook } from "@testing-library/react";

import { useGalleryFilter } from "./useGalleryFilter";

describe("useGalleryFilter", () => {
  it("사진 옵션을 선택한다", () => {
    const { result } = renderHook(() => useGalleryFilter());

    act(() => result.current.selectOption("mine"));

    expect(result.current.selectedOption).toBe("mine");
  });

  it("폴더를 선택하면 사진 옵션을 전체로 초기화한다", () => {
    const { result } = renderHook(() => useGalleryFilter());

    act(() => result.current.selectOption("others"));
    act(() => result.current.selectFolder(501));

    expect(result.current.selectedFolderId).toBe(501);
    expect(result.current.selectedOption).toBe("all");
  });
});
