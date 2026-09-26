import type { DownloadTarget } from "./types";

/**
 * 진행 바가 그리는 상태. 파일 단위로 흩어져 오는 이벤트를 한 판으로 합친 것이다.
 *
 * 업로드와 달리 **분모가 처음부터 확정이다.** 목록이 이미 각 미디어의 `size` 를 알려줬고
 * 발급 왕복 같은 것도 없다. 그래서 `withTargets` 에 해당하는 보정 단계가 없다.
 *
 * 상태를 만드는 쪽은 `useMediaDownload` 다. 여기는 순수 함수만 둔다.
 */

/**
 * 지금 뭘 하는 중인지. 퍼센트만으로는 설명이 안 되는 구간이 있어서 따로 둔다.
 *
 * 다 받고 나서 zip 으로 묶는 동안은 네트워크가 조용하다. 퍼센트만 보면 100% 에서
 * 멈춘 것처럼 보여서, 그 구간에는 "묶는 중"이라고 말해줘야 한다.
 *
 * `zipping` 다음에 `receiving` 이 따로 있는 이유도 같다. 서버가 다 묶었다고 알려온 뒤에도
 * **그 zip 을 실제로 내려받는 시간이 통째로 남아 있다.** 폰에서는 이쪽이 오히려 더 긴데,
 * 압축 진행률만 보고 있으면 그 구간 내내 100% 에 붙어 멈춘 것처럼 보인다.
 */
export type DownloadPhase = "fetching" | "zipping" | "receiving" | "sharing";

export interface DownloadProgressState {
  phase: DownloadPhase;
  /** 받을 장수. 판이 시작할 때 확정된다. */
  totalCount: number;
  /** 퍼센트의 분모. 목록이 알려준 크기의 합이다. */
  totalBytes: number;
  /** 다 받은 장수. 실패한 것은 세지 않는다. */
  completedCount: number;
  totalByMediaId: Record<number, number>;
  /**
   * mediaId → 마지막으로 보고된 수신 바이트.
   * 더하지 않고 **덮어쓴다** — 한 파일의 진행률은 그 파일 안에서만 누적된다.
   */
  loadedByMediaId: Record<number, number>;
  /**
   * 서버가 알려준 압축 진행률(0~100). zip 일 때만 채워진다.
   * 받은 바이트로 세는 `loadedByMediaId` 와 분리해 둔다 — 세는 대상이 아예 다르다.
   */
  zipPercent: number | null;
  /**
   * 다 묶인 zip 을 내려받는 동안의 바이트. `zipPercent` 와 분리해 둔다 —
   * 압축이 100% 로 끝난 뒤에 0 부터 다시 시작하는, 세는 대상이 아예 다른 구간이다.
   */
  zipBytes: { loaded: number; total: number } | null;
}

export const startDownloadProgress = (targets: DownloadTarget[]): DownloadProgressState => ({
  phase: "fetching",
  totalCount: targets.length,
  totalBytes: targets.reduce((sum, target) => sum + target.size, 0),
  completedCount: 0,
  totalByMediaId: Object.fromEntries(targets.map((target) => [target.mediaId, target.size])),
  loadedByMediaId: {},
  zipPercent: null,
  zipBytes: null,
});

export const withPhase = (
  state: DownloadProgressState,
  phase: DownloadPhase,
): DownloadProgressState => ({ ...state, phase });

export const withProgress = (
  state: DownloadProgressState,
  { mediaId, loaded, total }: { mediaId: number; loaded: number; total: number },
): DownloadProgressState => ({
  ...state,
  loadedByMediaId: {
    ...state.loadedByMediaId,
    // 목록이 알려준 크기를 넘겨 세지 않는다. 분모는 그대로인데 분자만 커지면 100% 를 넘긴다.
    [mediaId]: Math.min(loaded, state.totalByMediaId[mediaId] ?? total),
  },
});

export const withDownloaded = (
  state: DownloadProgressState,
  mediaId: number,
): DownloadProgressState => ({
  ...state,
  completedCount: state.completedCount + 1,
  loadedByMediaId: {
    ...state.loadedByMediaId,
    // 마지막 진행률 이벤트가 99% 에서 끊길 수 있다. 끝난 파일은 다 받은 것으로 친다.
    [mediaId]: state.totalByMediaId[mediaId] ?? state.loadedByMediaId[mediaId] ?? 0,
  },
});

export const withZipProgress = (
  state: DownloadProgressState,
  percent: number,
): DownloadProgressState => ({ ...state, zipPercent: Math.min(Math.max(percent, 0), 100) });

export const withZipBytes = (
  state: DownloadProgressState,
  { loaded, total }: { loaded: number; total: number },
): DownloadProgressState => ({ ...state, zipBytes: { loaded, total } });

const clampPercent = (percent: number) => Math.min(Math.max(percent, 0), 100);

export const loadedBytesOf = (state: DownloadProgressState) =>
  Object.values(state.loadedByMediaId).reduce((sum, loaded) => sum + loaded, 0);

/**
 * 0~100 의 정수. **파일 수가 아니라 바이트 기준이다** — 업로드 바와 같은 이유로,
 * 3.4MB 사진과 80MB 영상을 같은 한 장으로 세면 바가 영상 구간에서 통째로 멈춘다.
 */
export const percentOf = (state: DownloadProgressState) => {
  // 묶인 zip 을 받는 중이라면 그쪽이 지금 움직이는 유일한 값이다. 압축 진행률보다 뒤에 오므로
  // 먼저 본다 — 이걸 뒤에 두면 100% 로 끝난 `zipPercent` 에 가려 바가 다시 멈춘다.
  if (state.zipBytes !== null) {
    if (state.zipBytes.total <= 0) {
      return 100;
    }

    return clampPercent(Math.floor((state.zipBytes.loaded / state.zipBytes.total) * 100));
  }

  // zip 은 서버가 진행률을 알려준다. 우리가 받은 바이트로 세면 압축 구간이 통째로 빠진다.
  if (state.zipPercent !== null) {
    return state.zipPercent;
  }

  if (state.totalBytes <= 0) {
    return 0;
  }

  return clampPercent(Math.floor((loadedBytesOf(state) / state.totalBytes) * 100));
};

/** 화면이 실제로 읽는 값. 내부 장부는 바깥으로 나가지 않는다. */
export interface DownloadProgressSnapshot {
  phase: DownloadPhase;
  completedCount: number;
  totalCount: number;
  percent: number;
}

export const snapshotOf = (state: DownloadProgressState): DownloadProgressSnapshot => ({
  phase: state.phase,
  completedCount: state.completedCount,
  totalCount: state.totalCount,
  percent: percentOf(state),
});
