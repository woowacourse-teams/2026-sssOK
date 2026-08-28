import { API_BASE_URL } from "@/shared/config";
import { runWithLimit, waitUnlessAborted } from "@/shared/lib";
import { createDownloadJob } from "../api/createDownloadJob";
import { DOWNLOAD_CONCURRENCY, INDIVIDUAL_SAVE_GAP_MS } from "../config";
import {
  downloadMessageOfError,
  downloadMessageOfStatus,
  isRetryableError,
  isRetryableStatus,
} from "../lib/downloadErrorMessage";
import { fetchMediaBlob } from "../lib/fetchMediaBlob";
import { saveBlob } from "../lib/saveBlob";
import { shareFiles } from "../lib/shareFiles";
import type { DownloadPhase } from "./downloadProgress";
import { pollDownloadJob } from "./pollDownloadJob";
import type { DownloadMode, DownloadOutcome, DownloadTarget, FailedDownload } from "./types";

/**
 * 고른 미디어를 사용자가 고른 방식으로 넘긴다.
 *
 * **방식에 따라 서버 API 가 완전히 갈린다.**
 * - `individual`·`share` → 단건 다운로드(B-6)를 장수만큼. 받아온 바이트를 파일로 떨구거나 시트로 넘긴다.
 * - `zip` → 압축 잡(B-7)을 하나 만들고, 서버가 묶는 동안 상태를 되묻는다. 프론트는 압축하지 않는다.
 */

export interface DownloadMediaParams {
  roomId: number;
  targets: DownloadTarget[];
  mode: DownloadMode;
  token: string;
  signal?: AbortSignal;
  onProgress?: (one: { mediaId: number; loaded: number; total: number }) => void;
  onDownloaded?: (mediaId: number) => void;
  onPhase?: (phase: DownloadPhase) => void;
  /** 서버 압축 진행률(0~100). zip 일 때만 온다. */
  onZipProgress?: (percent: number) => void;
}

interface FetchedMedia {
  blob: Blob;
  target: DownloadTarget;
}

const toFile = ({ blob, target }: FetchedMedia) =>
  // 공유 시트는 Blob 이 아니라 File 을 요구한다. 이름이 있어야 사진첩에 제대로 들어간다.
  new File([blob], target.fileName, { type: blob.type || target.mimeType });

/** B-6 은 302 로 스토리지를 가리킨다. fetch 가 그 리다이렉트를 그대로 따라간다. */
const downloadUrlOf = (roomId: number, mediaId: number) =>
  `${API_BASE_URL}/rooms/${roomId}/downloads/media/${mediaId}`;

/** 장수만큼 단건 다운로드를 돌린다. 실패는 값으로 모으고 나머지는 계속 받는다. */
const fetchAll = async (
  { roomId, targets, token, signal, onProgress, onDownloaded }: DownloadMediaParams,
  failed: FailedDownload[],
) => {
  let aborted = false;

  const results = await runWithLimit(targets, DOWNLOAD_CONCURRENCY, async (target) => {
    const result = await fetchMediaBlob({
      url: downloadUrlOf(roomId, target.mediaId),
      token,
      size: target.size,
      signal,
      onProgress: (loaded, total) => onProgress?.({ mediaId: target.mediaId, loaded, total }),
    });

    if (result.type === "aborted") {
      aborted = true;

      return null;
    }

    if (result.type === "failure") {
      failed.push({
        mediaId: target.mediaId,
        fileName: target.fileName,
        status: result.status,
      });

      return null;
    }

    onDownloaded?.(target.mediaId);

    return { blob: result.blob, target };
  });

  return { fetched: results.filter((one): one is FetchedMedia => one !== null), aborted };
};

export const downloadMedia = async (params: DownloadMediaParams): Promise<DownloadOutcome> => {
  const { roomId, targets, mode, token, signal, onPhase, onZipProgress } = params;
  const failed: FailedDownload[] = [];

  if (mode === "zip") {
    // 압축은 서버가 한다. 프론트는 잡을 만들고 끝날 때까지 되묻기만 한다.
    let job;

    try {
      job = await createDownloadJob({
        roomId,
        token,
        mediaIds: targets.map((target) => target.mediaId),
      });
    } catch (error) {
      /*
       * 잡을 만들지도 못했다 (429 동시 3개 초과·410 기한 지남 등).
       * **폴링을 시작하지 않는다** — 되물을 잡 번호 자체가 없다 (#121 완료 조건).
       */
      return {
        type: "failed",
        reason: downloadMessageOfError(error),
        isRetryable: isRetryableError(error),
      };
    }

    onPhase?.("zipping");

    const settled = await pollDownloadJob({
      roomId,
      jobId: job.jobId,
      token,
      signal,
      onProgress: (progress) => onZipProgress?.(progress.progress),
    });

    if (settled === null) {
      return { type: "aborted" };
    }

    if (settled.status !== "READY" || settled.downloadUrl === null) {
      return {
        type: "failed",
        reason: settled.failureReason ?? "압축에 실패했어요",
        // EXPIRED 든 FAILED 든 새 잡을 만들면 된다. 사용자가 고칠 것이 없는 실패다.
        isRetryable: true,
      };
    }

    /*
     * 서명 URL 을 받아서 저장한다.
     *
     * **실서버에 붙일 때는 그냥 그 주소로 이동시키는 편이 낫다.** 서명 URL 에
     * `Content-Disposition: attachment` 가 실려 있어 이동만으로 저장되고, 그러면
     * 스토리지 CORS 도 필요 없고 수 GB 짜리 zip 을 메모리에 올리지도 않는다.
     * 지금 받아오는 이유는 목(MSW)이 다른 오리진의 이동을 가로챌 수 없어서다.
     */
    const zip = await fetchMediaBlob({ url: settled.downloadUrl, size: 0, signal });

    if (zip.type === "aborted") {
      return { type: "aborted" };
    }

    if (zip.type === "failure") {
      return {
        type: "failed",
        reason: downloadMessageOfStatus(zip.status),
        isRetryable: isRetryableStatus(zip.status),
      };
    }

    saveBlob(zip.blob, settled.fileName);

    return { type: "saved", savedCount: settled.mediaCount, failed };
  }

  const { fetched, aborted } = await fetchAll(params, failed);

  if (aborted || signal?.aborted) {
    return { type: "aborted" };
  }

  if (fetched.length === 0) {
    return { type: "empty", failed };
  }

  if (mode === "share") {
    onPhase?.("sharing");

    const files = fetched.map(toFile);
    const shared = await shareFiles(files);

    if (shared.type === "shared") {
      return { type: "saved", savedCount: files.length, failed };
    }

    if (shared.type === "dismissed") {
      return { type: "dismissed" };
    }

    // 시트가 안 열렸다. 받아온 것은 멀쩡하니 버리지 않고, 탭 한 번을 더 받으러 화면으로 넘긴다.
    return { type: "readyToShare", files, failed };
  }

  for (const [index, media] of fetched.entries()) {
    saveBlob(media.blob, media.target.fileName);

    // 마지막 한 장 뒤에는 기다릴 이유가 없다.
    if (index < fetched.length - 1 && !(await waitUnlessAborted(INDIVIDUAL_SAVE_GAP_MS, signal))) {
      // 저장은 이미 시작된 것들이라 되돌릴 수 없다. 남은 것만 그만둔다.
      return { type: "saved", savedCount: index + 1, failed };
    }
  }

  return { type: "saved", savedCount: fetched.length, failed };
};
