import { z } from "zod";
import { MediaSchema } from "../../../entities/media/model/types";
export const IssuedUploadSchema = z.object({
  mediaId: z.number(),
  fileName: z.string(),
  uploadUrl: z.string(),
  method: z.string(),
  headers: z.record(z.string(), z.string()),
  expiresIn: z.number(),
});
export const RejectedFileSchema = z.object({
  fileName: z.string(),
  code: z.union([
    z.literal("FILE_TOO_LARGE"),
    z.literal("UNSUPPORTED_MEDIA_TYPE"),
    z.literal("INVALID_PARAM"),
  ]),
  message: z.string(),
});
export const IssueUploadUrlsResponseSchema = z.object({
  issued: z.array(IssuedUploadSchema),
  rejected: z.array(RejectedFileSchema),
});
export const FailedRegistrationSchema = z.object({
  mediaId: z.number(),
  code: z.union([
    z.literal("MEDIA_NOT_FOUND"),
    z.literal("UPLOAD_NOT_COMPLETED"),
    z.literal("UPLOAD_ALREADY_COMPLETED"),
    z.literal("FILE_TOO_LARGE"),
    z.literal("UNSUPPORTED_MEDIA_TYPE"),
  ]),
  message: z.string(),
});
export const RegisterMediaResponseSchema = z.object({
  registered: z.array(MediaSchema),
  failed: z.array(FailedRegistrationSchema),
});
export const ReissuedUploadSchema = IssuedUploadSchema.extend(
  z.object({
    retryCount:
      z.number() /** 서버가 허용하는 재발급 한도. 넘으면 429 라 자동 재시도를 멈춰야 한다. */,
    maxRetryCount: z.number(),
  }).shape,
);

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
export type IssuedUpload = z.infer<typeof IssuedUploadSchema>;

/**
 * 파일 자체가 조건에 안 맞아 발급되지 않은 것.
 * 다시 눌러도 똑같이 실패하므로 재시도 대상이 아니다 — `failed` 와 다르게 보여야 한다.
 */
export type RejectedFile = z.infer<typeof RejectedFileSchema>;

export type IssueUploadUrlsResponse = z.infer<typeof IssueUploadUrlsResponseSchema>;

export interface ReissueUploadUrlRequest {
  /** 크기가 바뀌었을 때만 보낸다. 서버가 신고 크기를 갱신한다. */
  size?: number;
}

export type ReissuedUpload = z.infer<typeof ReissuedUploadSchema>;

export interface RegisterMediaRequest {
  mediaIds: number[];
}

/**
 * 등록 응답에 실려오는 미디어. 갤러리 목록도 같은 모양을 쓴다 —
 * 그래서 정의는 `entities/media` 에 두고 여기서는 다시 내보내기만 한다.
 */
export type { Media, MediaStatus } from "@/entities/media";

/**
 * 등록에서 미디어 단위로 갈라져 내려오는 실패.
 * `UPLOAD_ALREADY_COMPLETED` 는 실패가 아니다 — 이미 올라간 것이라 성공으로 합친다.
 */
export type FailedRegistration = z.infer<typeof FailedRegistrationSchema>;

export type RegisterMediaResponse = z.infer<typeof RegisterMediaResponseSchema>;
