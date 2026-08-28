import { waitUnlessAborted } from "@/shared/lib";
import { getDownloadJobProgress } from "../api/getDownloadJobProgress";
import { POLL_INTERVAL_MS } from "../config";
import type { DownloadJobProgress } from "../api/types";

/**
 * 압축이 끝날 때까지 잡 상태를 되묻는다.
 *
 * 서버가 먼저 알려주지 않아서 화면이 계속 물어봐야 한다. **멈추는 조건이 핵심이다** —
 * `READY`·`FAILED`·`EXPIRED` 셋 중 하나가 되면 그만둔다. 이걸 안 걸면 영원히 돈다.
 */

/** 더 물어봐도 소용없는 상태. 여기 닿으면 폴링이 끝난다. */
const SETTLED = ["READY", "FAILED", "EXPIRED"] as const;

const isSettled = (progress: DownloadJobProgress) =>
  (SETTLED as readonly string[]).includes(progress.status);

export interface PollDownloadJobParams {
  roomId: number;
  jobId: string;
  token: string;
  signal?: AbortSignal;
  /** 한 번 물어볼 때마다. 진행 바가 이걸로 갱신된다. */
  onProgress?: (progress: DownloadJobProgress) => void;
}

/**
 * 끝난 상태의 응답을 돌려준다. 중단되면 `null` 이다 —
 * 취소는 실패가 아니라서, 부르는 쪽이 실패 안내를 띄우면 안 된다.
 */
export const pollDownloadJob = async ({
  roomId,
  jobId,
  token,
  signal,
  onProgress,
}: PollDownloadJobParams): Promise<DownloadJobProgress | null> => {
  for (;;) {
    if (signal?.aborted) {
      return null;
    }

    const progress = await getDownloadJobProgress({ roomId, jobId, token });

    onProgress?.(progress);

    if (isSettled(progress)) {
      return progress;
    }

    // 기다리는 중에 취소되면 곧바로 깨어난다. 그냥 setTimeout 이면 취소 뒤에도 한 번 더 물어본다.
    if (!(await waitUnlessAborted(POLL_INTERVAL_MS, signal))) {
      return null;
    }
  }
};
