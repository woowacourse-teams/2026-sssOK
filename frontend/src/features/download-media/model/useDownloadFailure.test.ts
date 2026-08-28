import { act, renderHook } from "@testing-library/react";

import { useDownloadFailure } from "./useDownloadFailure";
import type { DownloadTarget } from "./types";

const targetOf = (mediaId: number): DownloadTarget => ({
  mediaId,
  fileName: `${mediaId}.jpg`,
  size: 100,
  mimeType: "image/jpeg",
});

const TARGETS = [targetOf(1), targetOf(2), targetOf(3)];

const settled = (outcome: Parameters<ReturnType<typeof useDownloadFailure>["settle"]>[0]) => {
  const { result } = renderHook(() => useDownloadFailure());

  act(() => result.current.settle(outcome, TARGETS, "individual"));

  return result;
};

describe("useDownloadFailure", () => {
  it("다 받았으면 모달을 띄우지 않는다", () => {
    expect(settled({ type: "saved", savedCount: 3, failed: [] }).current.failure).toBeNull();
  });

  it("취소는 실패가 아니다", () => {
    expect(settled({ type: "aborted" }).current.failure).toBeNull();
  });

  it("일부만 못 받으면 그 장수와 사유를 남긴다", () => {
    const result = settled({
      type: "saved",
      savedCount: 2,
      failed: [{ mediaId: 3, fileName: "3.jpg", status: 409 }],
    });

    expect(result.current.failure).toMatchObject({ count: 1, message: "아직 처리 중이에요" });
  });

  it("사유가 섞이면 어느 한쪽으로 단정하지 않는다", () => {
    const result = settled({
      type: "empty",
      failed: [
        { mediaId: 1, fileName: "1.jpg", status: 404 },
        { mediaId: 2, fileName: "2.jpg", status: 409 },
      ],
    });

    expect(result.current.failure?.message).toBe("받지 못했어요");
  });

  it("재시도 대상에서 없는 사진은 빼고 기다리면 되는 것만 남긴다", () => {
    const result = settled({
      type: "empty",
      failed: [
        { mediaId: 1, fileName: "1.jpg", status: 404 },
        { mediaId: 2, fileName: "2.jpg", status: 409 },
      ],
    });

    expect(result.current.failure?.targets).toEqual([targetOf(2)]);
  });

  it("판 전체가 무너지면 장수를 세지 않고 고른 것 전부를 재시도 대상으로 둔다", () => {
    const result = settled({
      type: "failed",
      reason: "받는 중인 요청이 많아요",
      isRetryable: true,
    });

    expect(result.current.failure).toMatchObject({ count: 0, targets: TARGETS });
  });

  it("되돌릴 수 없는 실패는 재시도 대상을 비운다", () => {
    const result = settled({ type: "failed", reason: "찾을 수 없어요", isRetryable: false });

    expect(result.current.failure?.targets).toEqual([]);
  });

  it("닫으면 상태가 지워진다", () => {
    const result = settled({
      type: "empty",
      failed: [{ mediaId: 1, fileName: "1.jpg", status: 0 }],
    });

    act(() => result.current.close());

    expect(result.current.failure).toBeNull();
  });
});
