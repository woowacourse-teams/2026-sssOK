import type { UploadFailureCode } from "../model/types";

/**
 * 실패 코드를 목록에 한 칸으로 들어갈 만큼 짧은 말로 바꾼다 (시안 07g).
 *
 * **문장이 아니라 꼬리표다.** 파일명 오른쪽 좁은 자리에 놓이므로 길면 잘린다.
 * 자세한 설명은 목록 위 부제가 한 번만 한다 — 파일마다 반복할 말이 아니다.
 */
const REASON_BY_CODE: Record<UploadFailureCode, string> = {
  UPLOAD_FAILED: "네트워크 오류",
  UPLOAD_RETRY_EXCEEDED: "재시도 한도 초과",
  UPLOAD_ABORTED: "취소함",
  UPLOAD_NOT_COMPLETED: "전송 미완료",
  MEDIA_NOT_FOUND: "찾을 수 없음",
  FILE_SIZE_EXCEEDED: "용량 초과",
};

export const uploadFailureReasonOf = (code: UploadFailureCode) => REASON_BY_CODE[code];
