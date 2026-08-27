import { useRef, useState } from "react";

import type { RejectedFile } from "../api/types";
import type { UploadProgressState } from "./uploadProgress";
import {
  snapshotOf,
  startUploadProgress,
  withProgress,
  withTargets,
  withUploaded,
} from "./uploadProgress";
import type { UploadResult } from "./types";
import { uploadFiles } from "./uploadFiles";

export interface UseMediaUploadOptions {
  roomId: number;
  token: string;
  /** 지금 열어둔 폴더. 없으면 루트로 올라간다. */
  folderIds?: number[];
  /** 발급이 거절한 파일. 업로드가 끝나기 전에 먼저 온다. */
  onRejected?: (rejected: RejectedFile[]) => void;
  /**
   * 등록까지 끝났을 때. 갤러리 갱신과 실패 모달(#74)이 여기서 갈린다.
   *
   * 취소했거나 새 판이 시작된 뒤에 끝난 판도 **알린다** — 그때까지 올라간 파일은 그대로
   * 등록돼 갤러리에 남기 때문이다 (#73). 다만 그런 판은 `superseded` 가 참이고,
   * **화면 상태를 건드리면 안 된다.** 지금 떠 있는 실패 모달을 지운 판이 그것이다.
   */
  onSettled?: (result: UploadResult, context: { superseded: boolean }) => void;
  /** 방·권한 문제라 배치 전체가 못 올라간 경우 (403·410 등). */
  onError?: (error: unknown) => void;
}

/**
 * 업로드 한 판을 굴리면서 진행 바가 볼 상태를 들고 있는다.
 *
 * `progress` 가 `null` 이면 업로드 중이 아니다 — 바를 띄울지 말지가 이 한 값으로 정해진다.
 */
export const useMediaUpload = ({
  roomId,
  token,
  folderIds,
  onRejected,
  onSettled,
  onError,
}: UseMediaUploadOptions) => {
  const [progress, setProgress] = useState<UploadProgressState | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  /**
   * 지금 화면이 따라가는 실행. 취소하거나 새로 시작하면 번호가 바뀐다.
   * 번호가 어긋난 실행의 콜백은 화면을 건드리지 못한다 — 취소한 판이 뒤늦게
   * 진행률을 흘려서 사라진 바를 되살리는 걸 막는다.
   */
  const runIdRef = useRef(0);

  const start = async (files: File[]) => {
    // 이미 한 판이 돌고 있다. 두 판을 겹치면 진행 바가 어느 쪽을 세는지 알 수 없다.
    if (files.length === 0 || abortRef.current !== null) {
      return;
    }

    const controller = new AbortController();

    abortRef.current = controller;
    runIdRef.current += 1;

    const runId = runIdRef.current;
    const isCurrent = () => runIdRef.current === runId;
    const update = (next: (state: UploadProgressState) => UploadProgressState) =>
      setProgress((current) => (current === null || !isCurrent() ? current : next(current)));

    // 발급 왕복을 기다리지 않고 먼저 띄운다. 장수와 바이트는 잠정값이다.
    setProgress(startUploadProgress(files));

    try {
      const result = await uploadFiles({
        roomId,
        files,
        token,
        folderIds,
        signal: controller.signal,
        onRejected,
        onStarted: (targets) => update((state) => withTargets(state, targets)),
        onProgress: (one) => update((state) => withProgress(state, one)),
        onUploaded: (one) => update((state) => withUploaded(state, one)),
      });

      // 취소한 판이라도 알린다. 취소 전에 올라간 파일은 그대로 등록돼 갤러리에 남는다 (#73).
      // 다만 이 판이 이미 밀려났다면 부르는 쪽이 화면을 건드리지 않도록 알려준다.
      onSettled?.(result, { superseded: !isCurrent() });
    } catch (error) {
      onError?.(error);
    } finally {
      if (isCurrent()) {
        abortRef.current = null;
        setProgress(null);
      }
    }
  };

  /**
   * 진행 중인 PUT 을 끊고 바를 곧바로 치운다.
   *
   * 완료 등록은 뒤에서 계속 나간다 — 중단은 "아직 안 올린 것을 그만두는" 것이지
   * "올린 것을 무르는" 게 아니다 (#73 완료 조건). 그 왕복을 기다리느라 바를 남겨두면
   * 사용자는 취소가 안 먹은 걸로 본다.
   */
  const cancel = () => {
    abortRef.current?.abort();
    abortRef.current = null;
    runIdRef.current += 1;
    setProgress(null);
  };

  return { progress: progress === null ? null : snapshotOf(progress), start, cancel };
};
