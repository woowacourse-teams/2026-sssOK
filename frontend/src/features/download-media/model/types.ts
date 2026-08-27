/**
 * 받는 방식. 사용자가 고른다 (003-selection-download "골라서 받기").
 *
 * - `individual` 한 장씩 파일로 저장한다.
 * - `zip`        서버가 묶어준 압축 파일 하나를 받는다.
 *
 * **장수로 자동 판단하지 않는다.** 결과물의 모양을 정하는 것은 사용자다.
 */
export type DownloadMode = "individual" | "zip";

/** 받을 대상 한 건. `MediaItem` 에서 필요한 것만 추린 모양이다. */
export interface DownloadTarget {
  mediaId: number;
  /** 화면에 보여줄 이름. 실제 저장 이름은 서버가 `Content-Disposition` 으로 정한다. */
  fileName: string;
  /** 목록이 알려준 크기. 퍼센트의 분모다. */
  size: number;
  mimeType: string;
}

export interface FailedDownload {
  mediaId: number;
  fileName: string;
  /** 0 이면 응답 자체를 못 받은 것이다 (회선 끊김·CORS 차단). */
  status: number;
}

/** 한 판의 결말. */
export type DownloadOutcome =
  | { type: "saved"; savedCount: number; failed: FailedDownload[] }
  | { type: "aborted" }
  /** 고른 것 중 한 장도 못 받았다. 실패 목록만 남는다. */
  | { type: "empty"; failed: FailedDownload[] }
  /** 판 전체가 무너졌다. 압축 실패이거나, 잡을 만드는 요청부터 거절당한 경우다. */
  | { type: "failed"; reason: string; isRetryable: boolean };
