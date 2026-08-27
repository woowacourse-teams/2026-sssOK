/**
 * 슬라이스 밖으로 여는 것만 둔다.
 * 나머지(`UploadButton`, `SelectionNotice`, `selectMediaFiles`, 검증 규칙)는 안에서만 쓴다 —
 * 여기 적는 순간 밖에서 써도 된다는 뜻이 되므로, 실제로 쓰는 곳이 생길 때 연다.
 */
export { MediaUploader } from "./ui/MediaUploader";

export { uploadFiles } from "./model/uploadFiles";
export { useMediaUpload } from "./model/useMediaUpload";
export { UploadProgressBar } from "./ui/UploadProgressBar";
export type {
  FailedUpload,
  UploadFailureCode,
  UploadFilesOptions,
  UploadProgress,
  UploadResult,
  UploadTargetInfo,
  UploadedFile,
} from "./model/types";
export type { UploadProgressSnapshot } from "./model/uploadProgress";
export type { UseMediaUploadOptions } from "./model/useMediaUpload";
export type { UploadProgressBarProps } from "./ui/UploadProgressBar";
export type { RejectedFile } from "./api/types";
