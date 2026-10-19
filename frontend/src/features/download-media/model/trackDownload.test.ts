import { track } from "@/shared/lib/analytics";
import { trackDownload, trackDownloadStarted } from "./trackDownload";
import type { FailedDownload } from "./types";

jest.mock("@/shared/lib/analytics", () => ({ track: jest.fn() }));

const failure = (mediaId: number): FailedDownload => ({
  mediaId,
  fileName: `IMG_${mediaId}.jpg`,
  status: 0,
});

const ROOM = { room_code: "7K93QX2S", role: "guest" } as const;

describe("trackDownload", () => {
  it("방 20장 중 5장을 골라 받으면 골라 받기로 남긴다", () => {
    trackDownload(
      { type: "saved", savedCount: 5, failed: [] },
      {
        mode: "zip",
        source: "gallery",
        selectedCount: 5,
        roomPhotoCount: 20,
        isSelectAll: false,
        durationMs: 1234.4,
        room: ROOM,
      },
    );

    expect(track).toHaveBeenCalledWith(
      "Photo Downloaded",
      {
        mode: "zip",
        source: "gallery",
        photo_count: 5,
        failed_count: 0,
        total_count: 20,
        is_select_all: false,
        duration_ms: 1234,
      },
      ROOM,
    );
  });

  it("방 전체를 골라 받으면 전체 받기로 남긴다", () => {
    trackDownload(
      { type: "saved", savedCount: 19, failed: [failure(1)] },
      {
        mode: "share",
        source: "gallery",
        selectedCount: 20,
        roomPhotoCount: 20,
        isSelectAll: true,
        durationMs: 1234.4,
        room: ROOM,
      },
    );

    expect(track).toHaveBeenCalledWith(
      "Photo Downloaded",
      {
        mode: "share",
        source: "gallery",
        photo_count: 19,
        failed_count: 1,
        total_count: 20,
        is_select_all: true,
        duration_ms: 1234,
      },
      ROOM,
    );
  });

  it("한 장도 못 받았으면 실패로 남긴다", () => {
    trackDownload(
      { type: "empty", failed: [failure(1), failure(2)] },
      {
        mode: "individual",
        source: "gallery",
        selectedCount: 2,
        roomPhotoCount: 20,
        isSelectAll: false,
        durationMs: 1234.4,
        room: ROOM,
      },
    );

    expect(track).toHaveBeenCalledWith(
      "Photo Download Failed",
      {
        mode: "individual",
        source: "gallery",
        reason: "all_failed",
        failed_count: 2,
        duration_ms: 1234,
      },
      ROOM,
    );
  });

  it("판 전체가 무너지면 사유와 함께 고른 장수만큼 실패로 남긴다", () => {
    trackDownload(
      { type: "failed", reason: "압축에 실패했어요", isRetryable: true },
      {
        mode: "zip",
        source: "gallery",
        selectedCount: 7,
        roomPhotoCount: 20,
        isSelectAll: false,
        durationMs: 1234.4,
        room: ROOM,
      },
    );

    expect(track).toHaveBeenCalledWith(
      "Photo Download Failed",
      {
        mode: "zip",
        source: "gallery",
        reason: "압축에 실패했어요",
        failed_count: 7,
        duration_ms: 1234,
      },
      ROOM,
    );
  });

  it.each([
    { type: "aborted" as const },
    { type: "dismissed" as const },
    { type: "readyToShare" as const, files: [], failed: [] },
  ])("$type 은 받은 것도 실패한 것도 아니라 남기지 않는다", (outcome) => {
    trackDownload(outcome, {
      mode: "share",
      source: "gallery",
      selectedCount: 3,
      roomPhotoCount: 20,
      isSelectAll: false,
      durationMs: 1234.4,
      room: ROOM,
    });

    expect(track).not.toHaveBeenCalled();
  });

  it("받기를 시작하면 방식·장수·용량을 남긴다", () => {
    trackDownloadStarted(
      [
        { mediaId: 1, fileName: "IMG_1.jpg", size: 1000, mimeType: "image/jpeg" },
        { mediaId: 2, fileName: "IMG_2.jpg", size: 2500, mimeType: "image/jpeg" },
      ],
      { mode: "share", source: "gallery", room: ROOM },
    );

    expect(track).toHaveBeenCalledWith(
      "Photo Download Started",
      { mode: "share", source: "gallery", photo_count: 2, total_bytes: 3500 },
      ROOM,
    );
  });
});
