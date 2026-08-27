import type { FailedUpload, UploadFailureCode } from "./types";

/**
 * 다시 올려도 결과가 같은 사유. 재시도 버튼을 주면 안 된다.
 *
 * - `UPLOAD_ABORTED` — 사용자가 직접 취소를 눌렀다. 실패가 아니다.
 *   "예기치 못한 이유로 실패했어요" 라고 되받으면 자기가 누른 취소를 사고로 읽는다.
 * - `FILE_SIZE_EXCEEDED` — 파일 자체가 한도를 넘었다. `rejected` 와 같은 성질이라
 *   같은 파일을 몇 번을 올려도 같은 자리에서 걸린다.
 */
const NOT_RETRYABLE: readonly UploadFailureCode[] = ["UPLOAD_ABORTED", "FILE_SIZE_EXCEEDED"];

/**
 * 실패분에서 다시 올릴 원본만 추린다. **실패 모달의 N 이 이 길이다** (#74) —
 * 세는 것과 다시 올리는 것이 같은 목록에서 나와야 "N장" 과 재시도 결과가 어긋나지 않는다.
 *
 * `UPLOAD_RETRY_EXCEEDED`(서버 재발급 한도 429)는 여기 **포함된다.** 재시도는 재발급이 아니라
 * 새 발급이라, mediaId 가 새로 생기면서 그 한도도 같이 처음으로 돌아간다.
 */
export const retryableFilesOf = (failed: FailedUpload[]): File[] =>
  failed.filter(({ code }) => !NOT_RETRYABLE.includes(code)).map(({ file }) => file);
