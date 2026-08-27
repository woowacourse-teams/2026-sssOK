/**
 * 받는 방식. 시트에서 사용자가 고른다 (003-selection-download "골라서 받기").
 *
 * - `individual` 한 장씩 파일로 저장한다. 데스크톱에서만 내준다.
 * - `zip`        서버가 묶어준 압축 파일 하나를 받는다.
 * - `share`      공유 시트를 띄워 사진첩에 넣는다. 폰에서 `individual` 자리를 대신한다.
 */
export type DownloadMode = "individual" | "zip" | "share";

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

/**
 * 한 판의 결말.
 *
 * `readyToShare` 가 따로 있는 이유는 사파리 때문이다. 공유 시트는 **사용자 제스처
 * 안에서만** 열리는데, 바이트를 받아오느라 `await` 를 한 번이라도 하면 그 제스처가
 * 만료된 것으로 본다. 그래서 받아온 뒤 시트가 거절당하면 실패로 두지 않고,
 * "탭 한 번만 더" 상태로 화면에 넘긴다.
 */
export type DownloadOutcome =
  | { type: "saved"; savedCount: number; failed: FailedDownload[] }
  | { type: "readyToShare"; files: File[]; failed: FailedDownload[] }
  | { type: "dismissed" }
  | { type: "aborted" }
  /** 고른 것 중 한 장도 못 받았다. 실패 목록만 남는다. */
  | { type: "empty"; failed: FailedDownload[] }
  /** 판 전체가 무너졌다. 압축 실패이거나, 잡을 만드는 요청부터 거절당한 경우다. */
  | { type: "failed"; reason: string; isRetryable: boolean };
