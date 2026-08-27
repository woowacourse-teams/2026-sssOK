import type { UploadProgress, UploadTargetInfo, UploadedFile } from "./types";

/**
 * 진행 바가 그리는 상태. 파일 단위로 흩어져 오는 이벤트를 배치 하나로 합친 것이다.
 *
 * **"3/30장" 과 "27%" 는 서로 다른 것을 센다.** 앞은 PUT 이 끝난 파일 수고,
 * 뒤는 지금까지 실제로 회선에 나간 바이트다. 큰 영상 한 장이 남으면
 * 장수는 29/30 인데 퍼센트는 40% 일 수 있다 — 어긋난 게 아니라 그게 맞는 표시다.
 *
 * 상태를 만드는 쪽은 `useMediaUpload` 다. 여기는 순수 함수만 둔다 —
 * 목이 즉시 응답해서 진행률은 목으로 확인할 수 없고, 계산만은 테스트로 묶어둘 수 있다.
 */
export interface UploadProgressState {
  /** 올릴 파일 장수. 발급 응답이 오면 거절분이 빠진 수로 확정된다 */
  totalCount: number;
  /** 퍼센트의 분모. 장수와 같은 시점에 확정된다 */
  totalBytes: number;
  /** PUT 이 끝난 장수 */
  completedCount: number;
  /** mediaId → 그 파일의 전체 바이트 */
  totalByMediaId: Record<number, number>;
  /**
   * mediaId → 마지막으로 보고된 전송 바이트.
   * 더하지 않고 **덮어쓴다** — 재발급으로 다시 올라가는 파일은 0 부터 다시 쌓이고,
   * 그때는 퍼센트가 뒤로 물러나는 게 맞다.
   */
  loadedByMediaId: Record<number, number>;
}

/**
 * 파일을 고른 직후의 상태. 아직 발급 전이라 장수·바이트 모두 **잠정값**이다.
 *
 * 발급 응답을 기다렸다가 바를 띄우면 왕복 한 번만큼 화면이 비어 있게 된다.
 * 잠정값으로 먼저 띄우고 `withTargets` 로 바로잡는다.
 */
export const startUploadProgress = (files: File[]): UploadProgressState => ({
  totalCount: files.length,
  totalBytes: files.reduce((sum, file) => sum + file.size, 0),
  completedCount: 0,
  totalByMediaId: {},
  loadedByMediaId: {},
});

/** 발급을 통과한 목록으로 분모를 확정한다. 거절된 파일은 여기서 빠진다. */
export const withTargets = (
  state: UploadProgressState,
  targets: UploadTargetInfo[],
): UploadProgressState => ({
  ...state,
  totalCount: targets.length,
  totalBytes: targets.reduce((sum, target) => sum + target.size, 0),
  totalByMediaId: Object.fromEntries(targets.map((target) => [target.mediaId, target.size])),
});

export const withProgress = (
  state: UploadProgressState,
  { mediaId, loaded, total }: UploadProgress,
): UploadProgressState => ({
  ...state,
  loadedByMediaId: {
    ...state.loadedByMediaId,
    // 발급이 알려준 크기를 넘겨 세지 않는다. 분모는 그대로인데 분자만 커지면 100% 를 넘긴다.
    [mediaId]: Math.min(loaded, state.totalByMediaId[mediaId] ?? total),
  },
});

export const withUploaded = (
  state: UploadProgressState,
  { mediaId }: UploadedFile,
): UploadProgressState => ({
  ...state,
  completedCount: state.completedCount + 1,
  loadedByMediaId: {
    ...state.loadedByMediaId,
    // 마지막 진행률 이벤트가 99% 에서 끊길 수 있다. 끝난 파일은 다 보낸 것으로 친다.
    [mediaId]: state.totalByMediaId[mediaId] ?? state.loadedByMediaId[mediaId] ?? 0,
  },
});

export const loadedBytesOf = (state: UploadProgressState) =>
  Object.values(state.loadedByMediaId).reduce((sum, loaded) => sum + loaded, 0);

/**
 * 0~100 의 정수. **파일 수가 아니라 바이트 기준이다** (#73 완료 조건) —
 * 3.4MB 사진과 80MB 영상을 같은 한 장으로 세면 바가 영상 구간에서 통째로 멈춘다.
 */
export const percentOf = (state: UploadProgressState) => {
  if (state.totalBytes <= 0) {
    return 0;
  }

  const percent = Math.floor((loadedBytesOf(state) / state.totalBytes) * 100);

  return Math.min(Math.max(percent, 0), 100);
};

/** 화면이 실제로 읽는 세 값. 내부 장부(`UploadProgressState`)는 바깥으로 나가지 않는다. */
export interface UploadProgressSnapshot {
  completedCount: number;
  totalCount: number;
  percent: number;
}

export const snapshotOf = (state: UploadProgressState): UploadProgressSnapshot => ({
  completedCount: state.completedCount,
  totalCount: state.totalCount,
  percent: percentOf(state),
});
