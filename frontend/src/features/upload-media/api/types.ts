/**
 * 업로드 3-API(발급·완료 등록·재발급)의 요청·응답 모양이다.
 * 지금은 목(src/mocks/handlers/upload.ts)이 이대로 답하고, 서버 구현은 #76 이다.
 */

/**
 * 발급 요청에 싣는 파일 한 건.
 * 서버는 확장자로 타입을 정하므로 `mimeType` 은 참고값이다 — 이 값으로 PUT 헤더를 만들면 안 된다.
 */
export interface UploadUrlRequestFile {
  fileName: string;
  mimeType: string;
  size: number;
}

export interface IssueUploadUrlsRequest {
  files: UploadUrlRequestFile[];
  /** 지금 열어둔 폴더. 비우면 루트로 올라간다. */
  folderIds?: number[];
}

/**
 * 발급과 재발급이 공유하는 모양.
 * `headers` 는 서명에 들어간 값이라 PUT 에 **그대로** 실어야 한다. 추측해서 만들면 403 이다.
 */
export interface IssuedUpload {
  mediaId: number;
  fileName: string;
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  expiresIn: number;
}

/**
 * 파일 자체가 조건에 안 맞아 발급되지 않은 것.
 * 다시 눌러도 똑같이 실패하므로 재시도 대상이 아니다 — `failed` 와 다르게 보여야 한다.
 */
export interface RejectedFile {
  fileName: string;
  code: "FILE_SIZE_EXCEEDED" | "UNSUPPORTED_FILE_TYPE" | "INVALID_PARAM";
  message: string;
}

export interface IssueUploadUrlsResponse {
  issued: IssuedUpload[];
  rejected: RejectedFile[];
}

export interface ReissueUploadUrlRequest {
  /** 크기가 바뀌었을 때만 보낸다. 서버가 신고 크기를 갱신한다. */
  size?: number;
}

export interface ReissuedUpload extends IssuedUpload {
  retryCount: number;
  /** 서버가 허용하는 재발급 한도. 넘으면 429 라 자동 재시도를 멈춰야 한다. */
  maxRetryCount: number;
}

export interface RegisterMediaRequest {
  mediaIds: number[];
}

export type MediaStatus = "RESERVED" | "PROCESSING" | "READY" | "FAILED";

/**
 * 등록 응답에 실려오는 미디어.
 * 나중에 갤러리 목록 조회도 같은 모양을 쓰므로, 그때 entities 로 올린다.
 */
export interface Media {
  mediaId: number;
  type: "IMAGE" | "VIDEO";
  fileName: string;
  mimeType: string;
  size: number;
  /** 워커가 만드는 값이라 PROCESSING 동안은 null 이다. */
  thumbnailUrl: string | null;
  originalUrl: string | null;
  width: number;
  height: number;
  /** 영상만 값이 있다. */
  duration: number | null;
  folderIds: number[];
  uploaderId: number;
  uploaderName: string;
  status: MediaStatus;
  uploadedAt: string;
}

/**
 * 등록에서 미디어 단위로 갈라져 내려오는 실패.
 * `UPLOAD_ALREADY_COMPLETED` 는 실패가 아니다 — 이미 올라간 것이라 성공으로 합친다.
 */
export interface FailedRegistration {
  mediaId: number;
  code:
    "MEDIA_NOT_FOUND" | "UPLOAD_NOT_COMPLETED" | "UPLOAD_ALREADY_COMPLETED" | "FILE_SIZE_EXCEEDED";
  message: string;
}

export interface RegisterMediaResponse {
  registered: Media[];
  failed: FailedRegistration[];
}
