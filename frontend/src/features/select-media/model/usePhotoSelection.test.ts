import { act, renderHook } from "@testing-library/react";

import { track } from "@/shared/lib/analytics";
import { usePhotoSelection } from "./usePhotoSelection";

jest.mock("@/shared/lib/analytics", () => ({ track: jest.fn() }));

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

  it("아무것도 안 고른 상태에서 처음 고를 때만 선택 시작을 남긴다", () => {
    const { result } = renderHook(() => usePhotoSelection([5011, 5012]));

    act(() => result.current.togglePhoto(5011));
    act(() => result.current.togglePhoto(5012));
    act(() => result.current.togglePhoto(5011));

    expect(track).toHaveBeenCalledTimes(1);
    expect(track).toHaveBeenCalledWith("Photo Selection Started", {});
  });

  it("전체 선택으로 고르기 시작해도 남기고, 전체 해제는 남기지 않는다", () => {
    const { result } = renderHook(() => usePhotoSelection([5011, 5012]));

    act(() => result.current.toggleAllPhotos());
    act(() => result.current.toggleAllPhotos());

    expect(track).toHaveBeenCalledTimes(1);
  });

  it("모두 해제한 뒤 다시 고르면 새 선택으로 남긴다", () => {
    const { result } = renderHook(() => usePhotoSelection([5011]));

    act(() => result.current.togglePhoto(5011));
    act(() => result.current.clearSelection());
    act(() => result.current.togglePhoto(5011));

    expect(track).toHaveBeenCalledTimes(2);
  });
});
