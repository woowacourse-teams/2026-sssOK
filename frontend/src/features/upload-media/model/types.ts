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
 * 발급을 통과해 실제로 올라갈 파일 한 건.
 * 진행 바는 이걸 받고서야 "몇 장 중 몇 장" 의 분모와 퍼센트의 분모를 확정한다 (#73) —
 * 고른 파일 전부가 올라가는 게 아니라, 거절분이 빠진 만큼만 올라가기 때문이다.
 */
export interface UploadTargetInfo {
  mediaId: number;
  fileName: string;
  /** 원본 `File.size`. 퍼센트의 분모로 쓰인다. */
  size: number;
}

/** PUT 이 끝난 파일 한 건. 아직 등록 전이라 갤러리에는 없다. */
export interface UploadedFile {
  mediaId: number;
  fileName: string;
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
   * 발급을 통과한 파일 목록. `onRejected` 직후, 첫 PUT 이 나가기 전에 한 번 불린다.
   * 진행 바가 고를 때 잡아둔 잠정 장수·바이트를 여기서 확정값으로 바로잡는다 (#73).
   */
  onStarted?: (targets: UploadTargetInfo[]) => void;
  /**
   * PUT 한 건이 끝날 때마다. 진행 바의 "완료 장수" 가 여기서 오른다 (#73).
   *
   * 진행률 이벤트로는 이걸 대신할 수 없다 — 100% 까지 보내고도 응답이 깨져
   * 재발급받아 처음부터 다시 올라가는 파일이 있다.
   */
  onUploaded?: (uploaded: UploadedFile) => void;
  /**
   * 중단하면 진행 중인 PUT 이 실제로 끊기고, 대기 중이던 파일은 출발하지 않는다.
   * 다만 **이미 올라간 파일은 그대로 등록한다** — 중단은 "아직 안 올린 것을 그만두는" 것이지
   * "올린 것을 무르는" 게 아니다 (#73 완료 조건).
   */
  signal?: AbortSignal;
}
