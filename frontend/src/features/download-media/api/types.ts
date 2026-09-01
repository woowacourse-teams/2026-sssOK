/** backend `DownloadJobStatus` 와 같은 목록이다. */
export type DownloadJobStatus = "QUEUED" | "RUNNING" | "READY" | "FAILED" | "EXPIRED";

/** B-7-1 응답. 압축은 아직 시작도 안 했고, 잡 번호만 온다. */
export interface DownloadJob {
  /**
   * 명세는 문자열(`"dl_7d1e93"`), 실제 백엔드 코드는 `Long` 이다.
   * **여기서는 문자열 연산을 하지 않고 URL 에 그대로 싣기만 한다** — 둘 중 무엇이 와도 동작한다.
   */
  jobId: string;
  status: DownloadJobStatus;
  /** 처리 중인 미디어를 뺀 실제 압축 대상 수. 고른 장수와 다를 수 있다. */
  mediaCount: number;
  totalSize: number;
  fileName: string;
}

/** B-7-2 응답. `downloadUrl`·`expiresAt` 은 READY 일 때만 채워진다. */
export interface DownloadJobProgress {
  jobId: string;
  status: DownloadJobStatus;
  /** 0~100. "압축 중..." 옆에 그대로 붙는다. */
  progress: number;
  mediaCount: number;
  fileName: string;
  downloadUrl: string | null;
  expiresAt: string | null;
  failureReason: string | null;
}

/**
 * B-6 다건 다운로드 URL 발급 응답의 파일 한 건.
 *
 * `downloadUrl` 은 스토리지 서명 URL 이다 — **우리 서버가 아니다.**
 * 받을 때 `Authorization` 을 붙이면 안 된다. 서명이 `host` 만 덮고 있어서
 * 헤더가 하나라도 더 붙으면 스토리지가 400 으로 거절하고, 그러면 CORS 응답 헤더도
 * 딸려오지 않아 브라우저에서는 원인 모를 네트워크 실패로만 보인다.
 */
export interface BatchDownloadFile {
  mediaId: number;
  fileName: string;
  downloadUrl: string;
  expiresAt: string;
}

export interface BatchDownload {
  files: BatchDownloadFile[];
}
