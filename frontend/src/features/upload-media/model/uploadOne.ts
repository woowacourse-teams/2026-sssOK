import { isApiError } from "@/shared/api";
import { reissueUploadUrl } from "../api/reissueUploadUrl";
import type { IssuedUpload } from "../api/types";
import { MAX_AUTO_RETRY, RETRY_BACKOFF_MS } from "../config";
import { putToStorage } from "../lib/putToStorage";
import { waitUnlessAborted } from "../lib/waitUnlessAborted";
import type { FailedUpload, UploadFailureCode, UploadProgress } from "./types";

export interface UploadOneParams {
  roomId: number;
  token: string;
  /** 발급 응답 한 건. 재발급을 받으면 이 자리를 새것으로 갈아끼운다. */
  issued: IssuedUpload;
  file: File;
  signal?: AbortSignal;
  onProgress?: (progress: UploadProgress) => void;
}

export type UploadOneResult = { ok: true; mediaId: number } | { ok: false; failure: FailedUpload };

/** 재발급이 던진 `ApiError` 를 이 파일의 실패 사유로 옮긴다. */
const failureCodeOf = (error: unknown): UploadFailureCode => {
  if (!isApiError(error)) {
    return "UPLOAD_FAILED";
  }

  // 서버 한도를 넘겼다. 더 받아봐야 또 429 라 자동 재시도를 멈춘다.
  if (error.status === 429) {
    return "UPLOAD_RETRY_EXCEEDED";
  }

  if (error.code === "MEDIA_NOT_FOUND") {
    return "MEDIA_NOT_FOUND";
  }

  // 방이 만료됐거나 권한이 사라진 경우도 여기로 온다. 그 둘은 이 파일만의 문제가 아니라
  // 배치 전체가 못 올라가는 상황인데, 지금은 파일 단위 실패로만 다룬다.
  return "UPLOAD_FAILED";
};

/**
 * 파일 **한 장**을 끝까지 책임진다. 깨지면 새 URL 을 받아 다시 올린다 —
 * 최초 1번 + 자동 재시도 `MAX_AUTO_RETRY` 번까지.
 *
 * 던지지 않는다. 성공도 실패도 값으로 돌려준다 — 한 장이 깨져도 나머지 파일이 계속돼야 한다.
 */
export const uploadOne = async ({
  roomId,
  token,
  issued,
  file,
  signal,
  onProgress,
}: UploadOneParams): Promise<UploadOneResult> => {
  const { mediaId, fileName } = issued;

  const failWith = (code: UploadFailureCode, message: string): UploadOneResult => ({
    ok: false,
    failure: { mediaId, fileName, code, message, file },
  });

  // 재발급을 받으면 URL 과 헤더가 갈린다. mediaId 와 파일은 그대로다.
  let target = issued;

  for (let attempt = 0; attempt <= MAX_AUTO_RETRY; attempt += 1) {
    const sent = await putToStorage({
      url: target.uploadUrl,
      headers: target.headers,
      file,
      signal,
      // 밑바닥은 바이트만 안다. 어느 파일인지는 여기서 붙인다.
      onProgress:
        onProgress && ((loaded, total) => onProgress({ mediaId, fileName, loaded, total })),
    });

    if (sent.type === "success") {
      return { ok: true, mediaId };
    }

    if (sent.type === "aborted") {
      return failWith("UPLOAD_ABORTED", "업로드를 취소했어요.");
    }

    // 마지막 시도였다면 새 URL 을 받아둘 이유가 없다.
    if (attempt === MAX_AUTO_RETRY) {
      break;
    }

    try {
      target = await reissueUploadUrl(roomId, mediaId, {}, token);
    } catch (error) {
      const code = failureCodeOf(error);

      return failWith(
        code,
        code === "UPLOAD_RETRY_EXCEEDED"
          ? "여러 번 실패했어요. 처음부터 다시 올려주세요."
          : "업로드에 실패했어요. 다시 시도해 주세요.",
      );
    }

    // 깨진 직후에 바로 다시 쏘면 같은 이유로 또 깨진다. 중단은 기다리는 중에도 먹는다.
    if (!(await waitUnlessAborted(RETRY_BACKOFF_MS[attempt], signal))) {
      return failWith("UPLOAD_ABORTED", "업로드를 취소했어요.");
    }
  }

  return failWith("UPLOAD_FAILED", "업로드에 실패했어요. 다시 시도해 주세요.");
};
