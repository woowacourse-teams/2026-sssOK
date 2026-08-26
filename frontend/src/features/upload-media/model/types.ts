import type { Media, RejectedFile } from "../api/types";

/**
 * 파일 한 건의 전송 진행. 배치 전체의 합산은 받는 쪽(#73)이 한다.
 * 여기서 합쳐서 내리면 "3/30장" 과 "27%" 중 하나만 만들 수 있게 된다.
 */
export interface UploadProgress {
  mediaId: number;
  fileName: string;
  loaded: number;
  /** XHR 이 전체 크기를 모를 때가 있어 그때는 `File.size` 를 쓴다. */
  total: number;
}

/**
 * 전송이 어쩌다 깨진 것. `rejected` 와 달리 재시도가 의미 있다.
 * 자동 재시도를 다 쓰고도 실패한 것만 여기로 온다.
 */
export type UploadFailureCode =
  /** PUT 이 자동 재시도까지 쓰고도 깨졌다 */
  | "UPLOAD_FAILED"
  /** 서버 재발급 한도(429)를 넘겼다. 처음부터 다시 올려야 한다 */
  | "UPLOAD_RETRY_EXCEEDED"
  /**
   * 사용자가 중단해서 PUT 을 끝내지 못했다.
   * 중단 시점에 이미 PUT 이 끝난 파일은 여기 오지 않고 그대로 등록된다 (#73).
   */
  | "UPLOAD_ABORTED"
  /** 등록 시점에 스토리지에 객체가 없었다 */
  | "UPLOAD_NOT_COMPLETED"
  | "MEDIA_NOT_FOUND"
  | "FILE_SIZE_EXCEEDED";

export interface FailedUpload {
  /** 발급을 통과한 뒤에만 실패할 수 있어서 항상 값이 있다. */
  mediaId: number;
  fileName: string;
  code: UploadFailureCode;
  message: string;
}

/**
 * 최종 결과. 화면은 이 셋을 각각 다르게 보여준다 —
 * `registered` 는 갤러리에, `failed` 는 재시도 버튼과 함께, `rejected` 는 재시도 없이.
 */
export interface UploadResult {
  registered: Media[];
  failed: FailedUpload[];
  rejected: RejectedFile[];
}

export interface UploadFilesOptions {
  roomId: number;
  files: File[];
  token: string;
  /** 지금 열어둔 폴더. 없으면 루트로 올라간다. */
  folderIds?: number[];
  /**
   * 발급이 거절한 파일을 먼저 알린다.
   * 업로드가 끝날 때까지 기다릴 이유가 없다 — 못 올린다는 건 발급 시점에 이미 확정이다.
   */
  onRejected?: (rejected: RejectedFile[]) => void;
  onProgress?: (progress: UploadProgress) => void;
  /**
   * 중단하면 진행 중인 PUT 이 실제로 끊기고, 대기 중이던 파일은 출발하지 않는다.
   * 다만 **이미 올라간 파일은 그대로 등록한다** — 중단은 "아직 안 올린 것을 그만두는" 것이지
   * "올린 것을 무르는" 게 아니다 (#73 완료 조건).
   */
  signal?: AbortSignal;
}
