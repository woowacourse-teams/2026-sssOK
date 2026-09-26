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

  it("전체 선택 뒤 일부를 해제하면 해제한 만큼만 빠진다", () => {
    const { result } = renderHook(() => usePhotoSelection([5011, 5012, 5013]));

    act(() => result.current.toggleAllPhotos());
    act(() => result.current.togglePhoto(5012));

    expect(result.current.selectedPhotoIds).toEqual([5011, 5013]);
    expect(result.current.isAllSelected).toBe(false);
  });

  it("전체 선택 뒤 새 사진이 보여도 저절로 선택되지 않는다", () => {
    const { result, rerender } = renderHook((ids: number[]) => usePhotoSelection(ids), {
      initialProps: [5011, 5012],
    });

    act(() => result.current.toggleAllPhotos());
    rerender([5013, 5011, 5012]);

    expect(result.current.selectedPhotoIds).toEqual([5011, 5012]);
    expect(result.current.isAllSelected).toBe(false);
  });

  it("선택한 사진이 목록에서 사라지면 사라진 장수만큼만 빠진다", () => {
    const { result, rerender } = renderHook((ids: number[]) => usePhotoSelection(ids), {
      initialProps: [5011, 5012, 5013],
    });

    act(() => result.current.toggleAllPhotos());
    act(() => result.current.removePhotos([5012]));
    rerender([5011, 5013]);

    expect(result.current.selectedPhotoIds).toEqual([5011, 5013]);
    expect(result.current.isAllSelected).toBe(true);
  });

  it("사진 선택을 초기화한다", () => {
    const { result } = renderHook(() => usePhotoSelection([5011]));

    act(() => result.current.togglePhoto(5011));
    act(() => result.current.clearSelection());

    expect(result.current.selectedPhotoIds).toEqual([]);
  });
});
