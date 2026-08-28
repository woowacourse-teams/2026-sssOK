import { useState } from "react";

import { retryableFailuresOf } from "./retryableFiles";
import type { FailedUpload, UploadResult } from "./types";

/**
 * 실패 모달이 떠 있는 동안의 상태를 들고 있는다 (#74).
 *
 * 다시 올리는 것 자체는 하지 않는다 — 재시도는 `useMediaUpload` 의 업로드 한 판을 그대로
 * 다시 굴리는 것이라, 두 훅을 서로 가리키게 만드는 대신 부르는 쪽에서 잇는다.
 *
 * 재시도로 돌린 판도 끝나면 `settle` 로 돌아온다. 그래서 또 깨진 게 있으면 모달이 다시 뜨고,
 * 전부 올라갔으면 그대로 닫힌다 — 몇 번을 돌든 같은 규칙 하나로 정해진다.
 */
export const useUploadFailure = () => {
  const [failures, setFailures] = useState<FailedUpload[]>([]);

  /**
   * 업로드 한 판이 끝날 때마다 부른다.
   *
   * 성공분은 건드리지 않는다. 이미 서버에 등록이 끝나 갤러리에 있는 것이라,
   * 실패가 있다고 해서 무를 것이 아니다 (#74 완료 조건).
   */
  const settle = (result: UploadResult) => setFailures(retryableFailuresOf(result.failed));

  const close = () => setFailures([]);

  return {
    /** 모달이 파일명과 사유를 여기서 읽는다. 모달의 N 도 이 길이다 */
    failures,
    /** 재시도가 다시 올릴 원본. 세는 목록과 같은 곳에서 나와야 결과가 어긋나지 않는다 */
    files: failures.map(({ file }) => file),
    /** 다시 올려볼 것이 하나도 없으면 모달을 띄우지 않는다 */
    isOpen: failures.length > 0,
    settle,
    close,
  };
};
