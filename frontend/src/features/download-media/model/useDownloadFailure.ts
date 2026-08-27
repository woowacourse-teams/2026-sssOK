import { useState } from "react";

import {
  DOWNLOAD_FALLBACK_MESSAGE,
  downloadMessageOfStatus,
  isRetryableStatus,
} from "../lib/downloadErrorMessage";
import type { DownloadMode, DownloadOutcome, DownloadTarget, FailedDownload } from "./types";

/**
 * 받기가 끝났을 때 못 받은 것이 있으면 그 상태를 들고 있는다 (#120 · #121 완료 조건).
 *
 * 업로드의 `useUploadFailure` 와 같은 자리다. 다시 받는 것 자체는 하지 않는다 —
 * 재시도는 받기 한 판을 그대로 다시 굴리는 것이라, 두 훅을 서로 가리키게 만드는 대신
 * 부르는 쪽(`SelectionDownloadBar`)에서 잇는다.
 */

export interface DownloadFailureState {
  /** 보여줄 사유 한 줄. */
  message: string;
  /**
   * 못 받은 장수. **0 이면 판 전체가 무너진 것이다** — 압축 잡을 만들지도 못한 경우라
   * 장수를 세는 것이 의미가 없다.
   */
  count: number;
  /** 재시도가 다시 받을 대상. 비어 있으면 재시도를 내주지 않는다. */
  targets: DownloadTarget[];
  mode: DownloadMode;
}

/**
 * 섞인 사유를 한 줄로 줄인다.
 *
 * **사유가 하나일 때만 그 사유를 말한다.** 404 와 409 가 섞였는데 "아직 처리 중이에요" 라고
 * 하면 절반은 거짓말이 된다. 섞였으면 공통 문구로 물러선다.
 */
const messageOfFailures = (failed: FailedDownload[]) => {
  const statuses = new Set(failed.map((one) => one.status));
  const [only] = [...statuses];

  return statuses.size === 1 ? downloadMessageOfStatus(only) : DOWNLOAD_FALLBACK_MESSAGE;
};

/** 다시 받아볼 만한 것만 남긴다. 없는 사진(404)은 다시 받아도 없다. */
const retryTargetsOf = (failed: FailedDownload[], targets: DownloadTarget[]) => {
  const retryableIds = new Set(
    failed.filter((one) => isRetryableStatus(one.status)).map((one) => one.mediaId),
  );

  return targets.filter((target) => retryableIds.has(target.mediaId));
};

export const useDownloadFailure = () => {
  const [failure, setFailure] = useState<DownloadFailureState | null>(null);

  /**
   * 받기 한 판이 끝날 때마다 부른다.
   *
   * 취소(`aborted`)와 시트를 닫은 것(`dismissed`)은 실패가 아니다 — 사용자가 스스로 그만둔
   * 것에 실패 모달을 띄우면 안 된다. 이미 저장된 것도 되돌리지 않는다.
   */
  const settle = (outcome: DownloadOutcome, targets: DownloadTarget[], mode: DownloadMode) => {
    if (outcome.type === "failed") {
      setFailure({
        message: outcome.reason,
        count: 0,
        targets: outcome.isRetryable ? targets : [],
        mode,
      });

      return;
    }

    if (outcome.type === "empty" || (outcome.type === "saved" && outcome.failed.length > 0)) {
      setFailure({
        message: messageOfFailures(outcome.failed),
        count: outcome.failed.length,
        targets: retryTargetsOf(outcome.failed, targets),
        mode,
      });

      return;
    }

    setFailure(null);
  };

  /** 결말을 만들지도 못하고 튄 예외. 원인을 알 수 없으니 재시도만 내준다. */
  const fail = (message: string, targets: DownloadTarget[], mode: DownloadMode) =>
    setFailure({ message, count: 0, targets, mode });

  const close = () => setFailure(null);

  return { failure, settle, fail, close };
};
