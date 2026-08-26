import { act, renderHook } from "@testing-library/react";

import { usePhotoSelection } from "./usePhotoSelection";

describe("usePhotoSelection", () => {
  it("사진을 개별 선택하고 다시 해제한다", () => {
    const { result } = renderHook(() => usePhotoSelection([5011, 5012]));

    act(() => result.current.togglePhoto(5011));
    expect(result.current.selectedPhotoIds).toEqual([5011]);

    act(() => result.current.togglePhoto(5011));
    expect(result.current.selectedPhotoIds).toEqual([]);
  });

  it("현재 보이는 사진 전체를 선택하고 다시 해제한다", () => {
    const { result } = renderHook(() => usePhotoSelection([5011, 5012]));

    act(() => result.current.toggleAllPhotos());
    expect(result.current.selectedPhotoIds).toEqual([5011, 5012]);
    expect(result.current.isAllSelected).toBe(true);

    act(() => result.current.toggleAllPhotos());
    expect(result.current.selectedPhotoIds).toEqual([]);
    expect(result.current.isAllSelected).toBe(false);
  });

  it("사진 선택을 초기화한다", () => {
    const { result } = renderHook(() => usePhotoSelection([5011]));

    act(() => result.current.togglePhoto(5011));
    act(() => result.current.clearSelection());

    expect(result.current.selectedPhotoIds).toEqual([]);
  });
});
