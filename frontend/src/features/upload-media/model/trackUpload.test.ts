import { track } from "@/shared/lib/analytics";
import type { Media, RejectedFile } from "../api/types";
import { trackUpload, trackUploadError } from "./trackUpload";
import type { FailedUpload, UploadResult } from "./types";

jest.mock("@/shared/lib/analytics", () => ({ track: jest.fn() }));

const media = (mediaId: number) => ({ mediaId }) as Media;
const failed = (mediaId: number): FailedUpload =>
  ({ mediaId, fileName: `IMG_${mediaId}.jpg`, code: "UPLOAD_FAILED", message: "" }) as FailedUpload;
const rejected = (fileName: string) => ({ fileName }) as RejectedFile;

const resultOf = (overrides: Partial<UploadResult>): UploadResult => ({
  registered: [],
  failed: [],
  rejected: [],
  alreadyRegistered: 0,
  ...overrides,
});

describe("trackUpload", () => {
  it("한 장이라도 올라가면 올린 장수·실패 장수·걸린 시간을 남긴다", () => {
    trackUpload(
      resultOf({ registered: [media(1), media(2)], alreadyRegistered: 1, failed: [failed(4)] }),
      1234.6,
    );

    expect(track).toHaveBeenCalledWith("Photo Uploaded", {
      photo_count: 3,
      failed_count: 1,
      duration_ms: 1235,
    });
  });

  it("한 장도 못 올렸으면 실패로 남기고 첫 실패 사유를 붙인다", () => {
    trackUpload(resultOf({ failed: [failed(1), failed(2)] }), 500);

    expect(track).toHaveBeenCalledTimes(1);
    expect(track).toHaveBeenCalledWith("Photo Upload Failed", {
      reason: "UPLOAD_FAILED",
      failed_count: 2,
    });
  });

  it("발급 단계에서 전부 거절됐어도 실패로 센다", () => {
    trackUpload(resultOf({ rejected: [rejected("big.mov")] }), 100);

    expect(track).toHaveBeenCalledWith("Photo Upload Failed", {
      reason: "REJECTED",
      failed_count: 1,
    });
  });

  it("올린 것도 실패한 것도 없으면 아무것도 남기지 않는다", () => {
    trackUpload(resultOf({}), 100);

    expect(track).not.toHaveBeenCalled();
  });
});

describe("trackUploadError", () => {
  it("판 전체가 무너지면 고른 장수만큼 실패로 남긴다", () => {
    trackUploadError(5);

    expect(track).toHaveBeenCalledWith("Photo Upload Failed", {
      reason: "REQUEST_FAILED",
      failed_count: 5,
    });
  });
});
