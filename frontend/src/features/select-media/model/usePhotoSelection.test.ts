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

it("드래그 범위에 맞춰 여러 사진의 선택을 한 번에 교체한다", () => {
  const { result } = renderHook(() => usePhotoSelection([1, 2, 3]));
  act(() => result.current.selectPhotos([1, 2, 3]));
  act(() => result.current.selectPhotos([1]));
  expect(result.current.selectedPhotoIds).toEqual([1]);
});

it("현재 목록의 드래그 선택을 교체해도 다른 목록에서 고른 사진은 보존한다", () => {
  const { result, rerender } = renderHook((ids: number[]) => usePhotoSelection(ids), {
    initialProps: [1, 2],
  });
  act(() => result.current.togglePhoto(1));
  rerender([2, 3]);
  act(() => result.current.selectPhotos([3]));
  rerender([1, 2, 3]);
  expect(result.current.selectedPhotoIds).toEqual([1, 3]);
});

it("목록에서 사라진 사진을 오래된 드래그 결과로 다시 선택하지 않는다", () => {
  const { result, rerender } = renderHook((ids: number[]) => usePhotoSelection(ids), {
    initialProps: [1, 2],
  });
  rerender([1]);
  act(() => result.current.selectPhotos([1, 2]));
  rerender([1, 2]);
  expect(result.current.selectedPhotoIds).toEqual([1]);
});
