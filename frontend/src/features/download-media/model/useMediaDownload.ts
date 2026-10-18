import { useRef, useState } from "react";

import { shareFiles, type ShareFilesResult } from "../lib/shareFiles";
import { downloadMedia } from "./downloadMedia";
import type { DownloadProgressState } from "./downloadProgress";
import {
  snapshotOf,
  startDownloadProgress,
  withDownloaded,
  withPhase,
  withProgress,
  withZipBytes,
  withZipProgress,
} from "./downloadProgress";
import { toShareBatches } from "./shareBatches";
import type { DownloadMode, DownloadOutcome, DownloadTarget, FailedDownload } from "./types";

export interface UseMediaDownloadOptions {
  roomId: number;
  token: string;
  /** 한 판이 끝났을 때. 실패 안내와 선택 해제가 여기서 갈린다. */
  onSettled?: (outcome: DownloadOutcome) => void;
  /** 압축이 만들어지지 않는 등, 판 전체가 무너진 경우. */
  onError?: (error: unknown) => void;
  /** 받아둔 묶음 하나를 공유 시트로 넘긴 결과. 묶음 기준을 조정할 근거로 남긴다. */
  onShareBatch?: (batch: ShareBatchResult) => void;
}

export interface ShareBatchResult {
  /** 몇 번째 묶음인지 (1부터) */
  batchIndex: number;
  batchCount: number;
  photoCount: number;
  totalBytes: number;
  result: ShareFilesResult["type"];
}

/**
 * 받아둔 것을 공유 시트로 넘기는 중인 판.
 * 앞 묶음부터 하나씩 넘기고, 넘긴 묶음은 `batches` 에서 빼 메모리에서 놓아준다.
 */
interface PendingShare {
  batches: File[][];
  batchCount: number;
  savedCount: number;
  /** 받기 단계에서 못 받은 것. 판이 끝날 때 결말에 그대로 싣는다. */
  failed: FailedDownload[];
}

/** 바가 그리는 값. 내부 장부(묶음 배열)는 바깥으로 내보내지 않는다. */
export interface PendingShareSnapshot {
  /** 지금 넘길 묶음이 몇 번째인지 (1부터) */
  batchIndex: number;
  batchCount: number;
  /** 지금 넘길 묶음의 장수 */
  nextCount: number;
  savedCount: number;
  remainingCount: number;
}

const snapshotOfShare = ({ batches, batchCount, savedCount }: PendingShare) => ({
  batchIndex: batchCount - batches.length + 1,
  batchCount,
  nextCount: batches[0].length,
  savedCount,
  remainingCount: batches.reduce((sum, batch) => sum + batch.length, 0),
});

/**
 * 받기 한 판을 굴리면서 진행 바가 볼 상태를 들고 있는다.
 *
 * `progress` 가 `null` 이면 받는 중이 아니다 — 바를 띄울지 말지가 이 한 값으로 정해진다.
 * `pendingShare` 가 차 있으면 바이트는 다 받았는데 공유 시트만 못 연 상태다 (`types.ts` 참고).
 */
export const useMediaDownload = ({
  roomId,
  token,
  onSettled,
  onError,
  onShareBatch,
}: UseMediaDownloadOptions) => {
  const [progress, setProgress] = useState<DownloadProgressState | null>(null);
  const [pendingShare, setPendingShare] = useState<PendingShare | null>(null);
  const [isSharing, setIsSharing] = useState(false);
  /**
   * 시트가 열려 있는 동안 다시 누른 것을 막는다. 상태는 다음 렌더에야 바뀌어서
   * 같은 틱에 두 번 누르면 둘 다 통과한다 — 그러면 사파리가 `InvalidStateError` 를 던진다.
   */
  const isSharingRef = useRef(false);
  const abortRef = useRef<AbortController | null>(null);
  /**
   * 지금 화면이 따라가는 실행. 취소하거나 새로 시작하면 번호가 바뀐다.
   * 번호가 어긋난 실행의 콜백은 화면을 건드리지 못한다 — 취소한 판이 뒤늦게
   * 진행률을 흘려서 사라진 바를 되살리는 걸 막는다.
   */
  const runIdRef = useRef(0);

  const start = async (targets: DownloadTarget[], mode: DownloadMode) => {
    // 이미 한 판이 돌고 있다. 두 판을 겹치면 진행 바가 어느 쪽을 세는지 알 수 없다.
    if (targets.length === 0 || abortRef.current !== null) {
      return;
    }

    const controller = new AbortController();

    abortRef.current = controller;
    runIdRef.current += 1;

    const runId = runIdRef.current;
    const isCurrent = () => runIdRef.current === runId;
    const update = (next: (state: DownloadProgressState) => DownloadProgressState) =>
      setProgress((current) => (current === null || !isCurrent() ? current : next(current)));

    setPendingShare(null);
    setProgress(startDownloadProgress(targets));

    try {
      const outcome = await downloadMedia({
        roomId,
        targets,
        mode,
        token,
        signal: controller.signal,
        onProgress: (one) => update((state) => withProgress(state, one)),
        onDownloaded: (mediaId) => update((state) => withDownloaded(state, mediaId)),
        onPhase: (phase) => update((state) => withPhase(state, phase)),
        onZipProgress: (percent) => update((state) => withZipProgress(state, percent)),
        onZipBytes: (bytes) => update((state) => withZipBytes(state, bytes)),
      });

      // 밀려난 판의 결말은 알리지 않는다. 업로드와 달리 받기는 서버에 남는 흔적이 없어,
      // 취소한 판이 뒤늦게 "3장 저장됨" 같은 안내를 띄울 이유가 전혀 없다.
      if (!isCurrent()) {
        return;
      }

      if (outcome.type === "readyToShare") {
        const batches = toShareBatches(outcome.files);

        setPendingShare({
          batches,
          batchCount: batches.length,
          savedCount: 0,
          failed: outcome.failed,
        });
      }

      onSettled?.(outcome);
    } catch (error) {
      if (isCurrent()) {
        onError?.(error);
      }
    } finally {
      if (isCurrent()) {
        abortRef.current = null;
        setProgress(null);
      }
    }
  };

  /**
   * 받아둔 묶음 중 **앞 묶음 하나만** 공유 시트로 넘긴다. **버튼 핸들러에서 바로 불러야 한다** —
   * 여기서 `await` 를 앞세우면 사파리가 제스처를 잃었다고 보고 다시 거절한다.
   * 그래서 이 함수 안에는 시트를 열기 전 `await` 가 하나도 없다.
   *
   * `share()` 성공은 "시트에서 어떤 동작을 끝까지 했다" 는 뜻이다. [이미지 저장]이 아닌
   * 동작(파일에 저장·AirDrop 등)도 똑같이 성공으로 오지만, 어느 쪽인지 알 길이 없어 저장으로 본다.
   */
  const share = async () => {
    if (pendingShare === null || isSharingRef.current) {
      return;
    }

    const [batch, ...rest] = pendingShare.batches;
    const batchIndex = pendingShare.batchCount - pendingShare.batches.length + 1;

    isSharingRef.current = true;
    setIsSharing(true);

    try {
      const shared = await shareFiles(batch);

      onShareBatch?.({
        batchIndex,
        batchCount: pendingShare.batchCount,
        photoCount: batch.length,
        totalBytes: batch.reduce((sum, file) => sum + file.size, 0),
        result: shared.type,
      });

      // 그냥 닫았거나 시트가 안 열렸다. 같은 묶음을 남겨, 같은 버튼을 다시 누를 수 있게 한다.
      if (shared.type !== "shared") {
        return;
      }

      const savedCount = pendingShare.savedCount + batch.length;

      if (rest.length > 0) {
        setPendingShare({ ...pendingShare, batches: rest, savedCount });

        return;
      }

      setPendingShare(null);
      onSettled?.({ type: "saved", savedCount, failed: pendingShare.failed });
    } catch (error) {
      onError?.(error);
    } finally {
      isSharingRef.current = false;
      setIsSharing(false);
    }
  };

  /**
   * 남은 묶음을 버린다. 사진첩에 더 넣지 않기로 한 경우다.
   * 이미 넘긴 묶음이 있으면 그만큼은 저장된 것이라 그 장수로 판을 끝낸다.
   */
  const dismissShare = () => {
    if (pendingShare === null || isSharingRef.current) {
      return;
    }

    setPendingShare(null);

    if (pendingShare.savedCount > 0) {
      onSettled?.({
        type: "saved",
        savedCount: pendingShare.savedCount,
        failed: pendingShare.failed,
      });
    }
  };

  /**
   * 진행 중인 요청을 끊고 바를 곧바로 치운다.
   *
   * 이미 저장이 시작된 파일은 되돌리지 않는다 — 중단은 "아직 안 받은 것을 그만두는" 것이지
   * "받은 것을 무르는" 게 아니다. 브라우저 다운로드 목록에 들어간 것은 그쪽에서 취소한다.
   */
  const cancel = () => {
    abortRef.current?.abort();
    abortRef.current = null;
    runIdRef.current += 1;
    setProgress(null);
    setPendingShare(null);
  };

  return {
    progress: progress === null ? null : snapshotOf(progress),
    pendingShare: pendingShare === null ? null : snapshotOfShare(pendingShare),
    isSharing,
    start,
    share,
    dismissShare,
    cancel,
  };
};
