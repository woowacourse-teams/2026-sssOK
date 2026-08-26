import { act, renderHook } from "@testing-library/react";

import type { FailedUpload, UploadFailureCode, UploadResult } from "./types";
import { useUploadFailure } from "./useUploadFailure";

let nextMediaId = 7000;

const failureOf = (code: UploadFailureCode, fileName = "해변.jpg"): FailedUpload => ({
  mediaId: (nextMediaId += 1),
  fileName,
  code,
  message: "",
  file: new File(["사진"], fileName, { type: "image/jpeg" }),
});

const resultOf = (failed: FailedUpload[]): UploadResult => ({
  registered: [],
  failed,
  rejected: [],
});

describe("useUploadFailure", () => {
  it("업로드 전에는 모달이 닫혀 있다", () => {
    const { result } = renderHook(() => useUploadFailure());

    expect(result.current.isOpen).toBe(false);
    expect(result.current.count).toBe(0);
  });

  it("전부 올라가면 모달을 띄우지 않는다", () => {
    const { result } = renderHook(() => useUploadFailure());

    act(() => result.current.settle(resultOf([])));

    expect(result.current.isOpen).toBe(false);
  });

  it("깨진 파일이 있으면 그 장수로 모달을 연다", () => {
    const { result } = renderHook(() => useUploadFailure());
    const failed = [failureOf("UPLOAD_FAILED", "첫째.jpg"), failureOf("UPLOAD_FAILED", "둘째.jpg")];

    act(() => result.current.settle(resultOf(failed)));

    expect(result.current.isOpen).toBe(true);
    expect(result.current.count).toBe(2);
    expect(result.current.files).toEqual([failed[0].file, failed[1].file]);
  });

  it("취소로만 끝났으면 모달을 띄우지 않는다 — 사용자가 스스로 끊은 것이다", () => {
    const { result } = renderHook(() => useUploadFailure());

    act(() => result.current.settle(resultOf([failureOf("UPLOAD_ABORTED")])));

    expect(result.current.isOpen).toBe(false);
  });

  it("닫으면 모달이 사라진다", () => {
    const { result } = renderHook(() => useUploadFailure());

    act(() => result.current.settle(resultOf([failureOf("UPLOAD_FAILED")])));
    act(() => result.current.close());

    expect(result.current.isOpen).toBe(false);
    expect(result.current.files).toEqual([]);
  });

  it("재시도한 판에서 또 깨지면 줄어든 장수로 다시 뜬다", () => {
    const { result } = renderHook(() => useUploadFailure());

    act(() =>
      result.current.settle(resultOf([failureOf("UPLOAD_FAILED"), failureOf("UPLOAD_FAILED")])),
    );
    act(() => result.current.settle(resultOf([failureOf("UPLOAD_FAILED")])));

    expect(result.current.count).toBe(1);
  });

  it("재시도한 판이 전부 성공하면 그대로 닫힌다", () => {
    const { result } = renderHook(() => useUploadFailure());

    act(() => result.current.settle(resultOf([failureOf("UPLOAD_FAILED")])));
    act(() => result.current.settle(resultOf([])));

    expect(result.current.isOpen).toBe(false);
  });
});
