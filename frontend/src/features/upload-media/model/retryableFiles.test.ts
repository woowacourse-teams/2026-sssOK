import { retryableFilesOf } from "./retryableFiles";
import type { FailedUpload, UploadFailureCode } from "./types";

let nextMediaId = 5000;

const failureOf = (code: UploadFailureCode, fileName = "해변.jpg"): FailedUpload => ({
  mediaId: (nextMediaId += 1),
  fileName,
  code,
  message: "",
  file: new File(["사진"], fileName, { type: "image/jpeg" }),
});

describe("retryableFilesOf", () => {
  it("전송이 깨진 파일은 다시 올린다", () => {
    const failed = [failureOf("UPLOAD_FAILED")];

    expect(retryableFilesOf(failed)).toEqual([failed[0].file]);
  });

  it("등록 단계 실패도 다시 올린다 — 스토리지에 객체가 없었을 뿐이다", () => {
    const failed = [failureOf("UPLOAD_NOT_COMPLETED"), failureOf("MEDIA_NOT_FOUND")];

    expect(retryableFilesOf(failed)).toHaveLength(2);
  });

  it("서버 재발급 한도를 넘겼어도 다시 올린다 — 새로 발급받으면 한도가 초기화된다", () => {
    const failed = [failureOf("UPLOAD_RETRY_EXCEEDED")];

    expect(retryableFilesOf(failed)).toEqual([failed[0].file]);
  });

  it("사용자가 취소한 것은 재시도 대상이 아니다", () => {
    expect(retryableFilesOf([failureOf("UPLOAD_ABORTED")])).toEqual([]);
  });

  it("한도를 넘은 파일은 재시도 대상이 아니다 — 몇 번을 올려도 같은 자리에서 걸린다", () => {
    expect(retryableFilesOf([failureOf("FILE_SIZE_EXCEEDED")])).toEqual([]);
  });

  it("섞여 있으면 재시도할 것만 순서대로 남긴다", () => {
    const broken = failureOf("UPLOAD_FAILED", "첫째.jpg");
    const aborted = failureOf("UPLOAD_ABORTED", "둘째.jpg");
    const oversized = failureOf("FILE_SIZE_EXCEEDED", "셋째.jpg");
    const notCompleted = failureOf("UPLOAD_NOT_COMPLETED", "넷째.jpg");

    expect(retryableFilesOf([broken, aborted, oversized, notCompleted])).toEqual([
      broken.file,
      notCompleted.file,
    ]);
  });

  it("실패가 없으면 빈 목록이다 — 모달이 뜨지 않는 조건이다", () => {
    expect(retryableFilesOf([])).toEqual([]);
  });
});
