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

  it("정렬 기준을 선택한다", () => {
    const { result } = renderHook(() => useGalleryFilter());

    act(() => result.current.selectSort("popular"));

    expect(result.current.selectedSort).toBe("popular");
  });

  it("사진 옵션을 바꾸면 정렬 기준을 업로드 순으로 초기화한다", () => {
    const { result } = renderHook(() => useGalleryFilter());

    act(() => result.current.selectSort("popular"));
    act(() => result.current.selectOption("liked"));

    expect(result.current.selectedSort).toBe("upload");
  });
});
