import { z } from "zod";
export const BatchDownloadFileSchema = z.object({
  mediaId: z.number(),
  fileName: z.string(),
  downloadUrl: z.string(),
  expiresAt: z.string(),
});
export const BatchDownloadSchema = z.object({ files: z.array(BatchDownloadFileSchema) });
export const DownloadJobStatusSchema = z.union([
  z.literal("QUEUED"),
  z.literal("RUNNING"),
  z.literal("READY"),
  z.literal("FAILED"),
  z.literal("EXPIRED"),
]);
export const DownloadJobSchema = z.object({
  /**
   * 명세는 문자열(`"dl_7d1e93"`), 실제 백엔드 코드는 `Long` 이다.
   * 두 형식 모두 검증한 뒤 문자열로 정규화하여 URL 경로에 사용한다.
   */
  jobId: z.union([z.string(), z.number().int().nonnegative()]).transform(String),
  status: DownloadJobStatusSchema,
  /** 처리 중인 미디어를 뺀 실제 압축 대상 수. 고른 장수와 다를 수 있다. */
  mediaCount: z.number(),
  totalSize: z.number(),
  fileName: z.string(),
});
export const DownloadJobProgressSchema = z.object({
  jobId: z.union([z.string(), z.number().int().nonnegative()]).transform(String),
  status: DownloadJobStatusSchema,
  /** 0~100. "압축 중..." 옆에 그대로 붙는다. */
  progress: z.number(),
  mediaCount: z.number(),
  fileName: z.string(),
  downloadUrl: z.union([z.string(), z.null()]),
  expiresAt: z.union([z.string(), z.null()]),
  failureReason: z.union([z.string(), z.null()]),
});
/** backend `DownloadJobStatus` 와 같은 목록이다. */
export type DownloadJobStatus = z.infer<typeof DownloadJobStatusSchema>;

/** B-7-1 응답. 압축은 아직 시작도 안 했고, 잡 번호만 온다. */
export type DownloadJob = z.infer<typeof DownloadJobSchema>;

/** B-7-2 응답. `downloadUrl`·`expiresAt` 은 READY 일 때만 채워진다. */
export type DownloadJobProgress = z.infer<typeof DownloadJobProgressSchema>;

/**
 * B-6 다건 다운로드 URL 발급 응답의 파일 한 건.
 *
 * `downloadUrl` 은 스토리지 서명 URL 이다 — **우리 서버가 아니다.**
 * 받을 때 `Authorization` 을 붙이면 안 된다. 서명이 `host` 만 덮고 있어서
 * 헤더가 하나라도 더 붙으면 스토리지가 400 으로 거절하고, 그러면 CORS 응답 헤더도
 * 딸려오지 않아 브라우저에서는 원인 모를 네트워크 실패로만 보인다.
 */
export type BatchDownloadFile = z.infer<typeof BatchDownloadFileSchema>;

export type BatchDownload = z.infer<typeof BatchDownloadSchema>;
