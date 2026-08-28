import { useRef, useState } from "react";

import { shareFiles } from "../lib/shareFiles";
import { downloadMedia } from "./downloadMedia";
import type { DownloadProgressState } from "./downloadProgress";
import {
  snapshotOf,
  startDownloadProgress,
  withDownloaded,
  withPhase,
  withProgress,
  withZipProgress,
} from "./downloadProgress";
import type { DownloadMode, DownloadOutcome, DownloadTarget } from "./types";

export interface UseMediaDownloadOptions {
  roomId: number;
  token: string;
  /** 한 판이 끝났을 때. 실패 안내와 선택 해제가 여기서 갈린다. */
  onSettled?: (outcome: DownloadOutcome) => void;
  /** 압축이 만들어지지 않는 등, 판 전체가 무너진 경우. */
  onError?: (error: unknown) => void;
}

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
}: UseMediaDownloadOptions) => {
  const [progress, setProgress] = useState<DownloadProgressState | null>(null);
  const [pendingShare, setPendingShare] = useState<File[] | null>(null);
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
      });

      // 밀려난 판의 결말은 알리지 않는다. 업로드와 달리 받기는 서버에 남는 흔적이 없어,
      // 취소한 판이 뒤늦게 "3장 저장됨" 같은 안내를 띄울 이유가 전혀 없다.
      if (!isCurrent()) {
        return;
      }

      if (outcome.type === "readyToShare") {
        setPendingShare(outcome.files);
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
   * 받아둔 파일로 공유 시트를 연다. **버튼 핸들러에서 바로 불러야 한다** —
   * 여기서 `await` 를 앞세우면 사파리가 제스처를 잃었다고 보고 다시 거절한다.
   * 그래서 이 함수 안에는 시트를 열기 전 `await` 가 하나도 없다.
   */
  const share = async () => {
    if (pendingShare === null) {
      return;
    }

    try {
      const shared = await shareFiles(pendingShare);

      if (shared.type !== "unsupported") {
        setPendingShare(null);
      }

      onSettled?.(
        shared.type === "shared"
          ? { type: "saved", savedCount: pendingShare.length, failed: [] }
          : { type: "dismissed" },
      );
    } catch (error) {
      onError?.(error);
    }
  };

  /** 받아둔 것을 버린다. 사진첩에 넣지 않기로 한 경우다. */
  const dismissShare = () => setPendingShare(null);

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
    pendingShare,
    start,
    share,
    dismissShare,
    cancel,
  };
};
