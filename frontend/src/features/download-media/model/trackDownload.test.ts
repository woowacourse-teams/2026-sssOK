import { track } from "@/shared/lib/analytics";
import { trackDownload } from "./trackDownload";
import type { FailedDownload } from "./types";

jest.mock("@/shared/lib/analytics", () => ({ track: jest.fn() }));

const failure = (mediaId: number): FailedDownload => ({
  mediaId,
  fileName: `IMG_${mediaId}.jpg`,
  status: 0,
});

describe("trackDownload", () => {
  it("방 20장 중 5장을 골라 받으면 골라 받기로 남긴다", () => {
    trackDownload(
      { type: "saved", savedCount: 5, failed: [] },
      { mode: "zip", selectedCount: 5, roomPhotoCount: 20 },
    );

    expect(track).toHaveBeenCalledWith("Photo Downloaded", {
      mode: "zip",
      photo_count: 5,
      failed_count: 0,
      total_count: 20,
      is_select_all: false,
    });
  });

  it("방 전체를 골라 받으면 전체 받기로 남긴다", () => {
    trackDownload(
      { type: "saved", savedCount: 19, failed: [failure(1)] },
      { mode: "share", selectedCount: 20, roomPhotoCount: 20 },
    );

    expect(track).toHaveBeenCalledWith("Photo Downloaded", {
      mode: "share",
      photo_count: 19,
      failed_count: 1,
      total_count: 20,
      is_select_all: true,
    });
  });

  it("한 장도 못 받았으면 실패로 남긴다", () => {
    trackDownload(
      { type: "empty", failed: [failure(1), failure(2)] },
      { mode: "individual", selectedCount: 2, roomPhotoCount: 20 },
    );

    expect(track).toHaveBeenCalledWith("Photo Download Failed", {
      mode: "individual",
      reason: "all_failed",
      failed_count: 2,
    });
  });

  it("판 전체가 무너지면 사유와 함께 고른 장수만큼 실패로 남긴다", () => {
    trackDownload(
      { type: "failed", reason: "압축에 실패했어요", isRetryable: true },
      { mode: "zip", selectedCount: 7, roomPhotoCount: 20 },
    );

    expect(track).toHaveBeenCalledWith("Photo Download Failed", {
      mode: "zip",
      reason: "압축에 실패했어요",
      failed_count: 7,
    });
  });

  it.each([
    { type: "aborted" as const },
    { type: "dismissed" as const },
    { type: "readyToShare" as const, files: [], failed: [] },
  ])("$type 은 받은 것도 실패한 것도 아니라 남기지 않는다", (outcome) => {
    trackDownload(outcome, { mode: "share", selectedCount: 3, roomPhotoCount: 20 });

    expect(track).not.toHaveBeenCalled();
  });
});
