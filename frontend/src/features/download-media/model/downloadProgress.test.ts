import {
  percentOf,
  snapshotOf,
  startDownloadProgress,
  withDownloaded,
  withPhase,
  withProgress,
} from "./downloadProgress";
import type { DownloadTarget } from "./types";

const targetOf = (mediaId: number, size: number): DownloadTarget => ({
  mediaId,
  size,
  fileName: `${mediaId}.jpg`,
  mimeType: "image/jpeg",
});

/** 3.4MB 사진 하나와 80MB 영상 하나. 장수와 바이트가 어긋나는 대표적인 조합이다. */
const PHOTO = targetOf(1, 3_400_000);
const VIDEO = targetOf(2, 80_000_000);

describe("startDownloadProgress", () => {
  it("장수와 바이트를 처음부터 확정한다 — 목록이 크기를 이미 알려줬다", () => {
    const state = startDownloadProgress([PHOTO, VIDEO]);

    expect(state.totalCount).toBe(2);
    expect(state.totalBytes).toBe(83_400_000);
    expect(state.phase).toBe("fetching");
  });
});

describe("percentOf", () => {
  it("장수가 아니라 바이트로 센다", () => {
    let state = startDownloadProgress([PHOTO, VIDEO]);

    // 사진은 다 받았지만 영상이 남았다. 장수로 세면 50% 인데, 실제로는 4% 다.
    state = withDownloaded(state, PHOTO.mediaId);

    expect(snapshotOf(state).completedCount).toBe(1);
    expect(percentOf(state)).toBe(4);
  });

  it("보고된 값이 그 파일의 크기를 넘겨도 100% 를 넘지 않는다", () => {
    let state = startDownloadProgress([PHOTO]);

    state = withProgress(state, {
      mediaId: PHOTO.mediaId,
      loaded: 9_000_000,
      total: 9_000_000,
    });

    expect(percentOf(state)).toBe(100);
  });

  it("받을 것이 없으면 0 이다 — 0 으로 나누지 않는다", () => {
    expect(percentOf(startDownloadProgress([]))).toBe(0);
  });

  it("한 파일의 진행률은 더하지 않고 덮어쓴다", () => {
    let state = startDownloadProgress([PHOTO]);

    state = withProgress(state, { mediaId: PHOTO.mediaId, loaded: 1_000_000, total: 3_400_000 });
    state = withProgress(state, { mediaId: PHOTO.mediaId, loaded: 1_700_000, total: 3_400_000 });

    expect(percentOf(state)).toBe(50);
  });

  it("다 받은 파일은 마지막 보고가 모자라도 전부 받은 것으로 친다", () => {
    let state = startDownloadProgress([PHOTO]);

    state = withProgress(state, { mediaId: PHOTO.mediaId, loaded: 3_399_000, total: 3_400_000 });
    state = withDownloaded(state, PHOTO.mediaId);

    expect(percentOf(state)).toBe(100);
  });
});

describe("withPhase", () => {
  it("단계만 바꾸고 세던 것은 그대로 둔다", () => {
    const state = withDownloaded(startDownloadProgress([PHOTO, VIDEO]), PHOTO.mediaId);
    const zipping = withPhase(state, "zipping");

    expect(snapshotOf(zipping)).toEqual({
      phase: "zipping",
      completedCount: 1,
      totalCount: 2,
      percent: 4,
    });
  });
});
