import { runWithLimit, waitUnlessAborted } from "@/shared/lib";
import { createBatchDownload } from "../api/createBatchDownload";
import type { BatchDownloadFile } from "../api/types";
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
 * - `individual`·`share` → 서명 URL 을 한 번에 받아(B-6) 장수만큼 내려받고,
 *   받아온 바이트를 파일로 떨구거나 공유 시트로 넘긴다.
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
  /** 다 묶인 zip 을 내려받는 동안의 바이트. zip 일 때만 온다. */
  onZipBytes?: (bytes: { loaded: number; total: number }) => void;
}

interface FetchedMedia {
  blob: Blob;
  target: DownloadTarget;
  /**
   * 저장할 이름. **목록의 이름이 아니라 발급 응답이 준 이름이다.**
   *
   * 같은 이름으로 올라간 사진을 함께 고르면 목록에는 둘 다 같은 이름으로 있다.
   * 그대로 저장하면 뒤엣것이 앞엣것을 덮고, 공유 시트에서는 같은 이름 둘이 한 번에 넘어간다.
   * 서버가 `DownloadFileNames.deduplicate` 로 "이름 (1)" 을 붙여 정리해 주는데,
   * 여기서 그걸 쓰지 않으면 그 처리가 통째로 버려진다.
   */
  fileName: string;
}

/**
 * 공유 시트는 Blob 이 아니라 File 을 요구한다. 이름이 있어야 사진첩에 제대로 들어간다.
 *
 * **MIME 은 목록이 알려준 값을 먼저 쓴다.** 스토리지가 `application/octet-stream` 을 내주면
 * `blob.type` 도 그 값이 되는데, 빈 문자열이 아니라 폴백이 걸리지 않는다. 그대로 넘기면
 * iOS 가 이미지로 보지 않아 공유 시트에 **"이미지 저장" 항목이 아예 뜨지 않는다.**
 * `target.mimeType` 은 서버가 허용 목록과 대조해 정한 값이라 이쪽이 더 믿을 만하다.
 */
const toFile = ({ blob, target, fileName }: FetchedMedia) =>
  new File([blob], fileName, { type: target.mimeType || blob.type });

/**
 * 장수만큼 내려받는다. 실패는 값으로 모으고 나머지는 계속 받는다.
 *
 * `issuedByMediaId` 는 발급 응답이다. 서명 URL 로 받을 때 **토큰을 넘기지 않는다** —
 * 우리 서버가 아니라 스토리지로 바로 가는 요청이라, 헤더를 얹으면 서명과 어긋난다
 * (`createBatchDownload` 주석 참고).
 */
const fetchAll = async (
  { targets, signal, onProgress, onDownloaded }: DownloadMediaParams,
  issuedByMediaId: Map<number, BatchDownloadFile>,
  failed: FailedDownload[],
) => {
  let aborted = false;

  const results = await runWithLimit(targets, DOWNLOAD_CONCURRENCY, async (target) => {
    const issuedFile = issuedByMediaId.get(target.mediaId);

    if (issuedFile === undefined) {
      // 서버가 대상에서 뺐다 (처리 중이거나 사라진 미디어다). 받아볼 주소 자체가 없다.
      failed.push({ mediaId: target.mediaId, fileName: target.fileName, status: 404 });

      return null;
    }

    const result = await fetchMediaBlob({
      url: issuedFile.downloadUrl,
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

    return { blob: result.blob, target, fileName: issuedFile.fileName };
  });

  return { fetched: results.filter((one): one is FetchedMedia => one !== null), aborted };
};

export const downloadMedia = async (params: DownloadMediaParams): Promise<DownloadOutcome> => {
  const { roomId, targets, mode, token, signal, onPhase, onZipProgress, onZipBytes } = params;
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
     *
     * **여기서도 진행률을 흘린다.** 압축이 끝났다고 기다림이 끝난 게 아니다 — 폰에서는
     * 이 구간이 압축보다 길 때가 많은데, 알리지 않으면 바가 100% 에 붙어 멈춰 보인다.
     * 분모는 잡이 알려준 원본 합계다. Content-Length 가 오면 `fetchMediaBlob` 이 그쪽을 쓴다.
     */
    onPhase?.("receiving");

    const zip = await fetchMediaBlob({
      url: settled.downloadUrl,
      size: job.totalSize,
      signal,
      onProgress: (loaded, total) => onZipBytes?.({ loaded, total }),
    });

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

  /*
   * 받기 전에 서명 URL 을 한 번에 발급받는다. 여기서 거절당하면(404 대상 없음·400 등)
   * 받을 것이 하나도 없으므로 판 전체가 무너진 것으로 본다 — zip 의 잡 생성 실패와 같다.
   */
  let issued;

  try {
    issued = await createBatchDownload({
      roomId,
      token,
      mediaIds: targets.map((target) => target.mediaId),
    });
  } catch (error) {
    return {
      type: "failed",
      reason: downloadMessageOfError(error),
      isRetryable: isRetryableError(error),
    };
  }

  const issuedByMediaId = new Map(issued.files.map((file) => [file.mediaId, file]));

  const { fetched, aborted } = await fetchAll(params, issuedByMediaId, failed);

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
    saveBlob(media.blob, media.fileName);

    // 마지막 한 장 뒤에는 기다릴 이유가 없다.
    if (index < fetched.length - 1 && !(await waitUnlessAborted(INDIVIDUAL_SAVE_GAP_MS, signal))) {
      // 저장은 이미 시작된 것들이라 되돌릴 수 없다. 남은 것만 그만둔다.
      return { type: "saved", savedCount: index + 1, failed };
    }
  }

  return { type: "saved", savedCount: fetched.length, failed };
};
