import { issueUploadUrls } from "../api/issueUploadUrls";
import { registerMedia } from "../api/registerMedia";
import type { Media } from "../api/types";
import { UPLOAD_CONCURRENCY } from "../config";
import { runWithLimit } from "../lib/runWithLimit";
import { pairWithFiles } from "./pairWithFiles";
import type { FailedUpload, UploadFilesOptions, UploadResult } from "./types";
import { uploadOne } from "./uploadOne";

/**
 * UI 없는 업로드 실행 모듈.
 *
 * 발급(한 번) → 스토리지 직접 PUT(파일 단위, 동시 `UPLOAD_CONCURRENCY` 개) → 완료 등록(한 번)
 * 을 수행하고, 결과를 성공·실패·거절로 갈라 돌려준다.
 *
 * 방·권한 문제(403·410 등)는 배치 전체가 못 올라가는 상황이라 `ApiError` 로 **던진다.**
 * 파일 하나하나의 실패는 던지지 않고 `failed` 로 모인다.
 */
export const uploadFiles = async ({
  roomId,
  files,
  token,
  folderIds,
  onRejected,
  onProgress,
  signal,
}: UploadFilesOptions): Promise<UploadResult> => {
  const { issued, rejected } = await issueUploadUrls(
    roomId,
    {
      files: files.map((file) => ({
        fileName: file.name,
        // 서버는 확장자로 타입을 정한다. 아이폰이 빈 문자열을 주더라도 그대로 보낸다.
        mimeType: file.type,
        size: file.size,
      })),
      ...(folderIds === undefined ? {} : { folderIds }),
    },
    token,
  );

  // 올릴 수 없다는 건 발급 시점에 이미 확정이다. 업로드가 끝날 때까지 숨길 이유가 없다.
  if (rejected.length > 0) {
    onRejected?.(rejected);
  }

  const results = await runWithLimit(
    pairWithFiles(files, issued, rejected),
    UPLOAD_CONCURRENCY,
    (target) =>
      uploadOne({ roomId, token, issued: target.issued, file: target.file, signal, onProgress }),
  );

  const uploadedIds: number[] = [];
  const failed: FailedUpload[] = [];

  for (const result of results) {
    if (result.ok) {
      uploadedIds.push(result.mediaId);
    } else {
      failed.push(result.failure);
    }
  }

  // 중단됐더라도 여기까지 올라간 것은 등록한다 — 중단은 "아직 안 올린 것을 그만두는" 것이지
  // "올린 것을 무르는" 게 아니다 (#73).
  if (uploadedIds.length === 0) {
    return { registered: [], failed, rejected };
  }

  const registerResult = await registerMedia(roomId, { mediaIds: uploadedIds }, token);
  const registered: Media[] = registerResult.registered;

  for (const failure of registerResult.failed) {
    // 이미 올라간 것이다. 실패로 보여주면 멀쩡히 올라간 사진을 실패로 보게 된다.
    // 응답에 Media 가 없어서 registered 에는 못 넣는다 — 갤러리를 다시 불러오면 나타난다.
    if (failure.code === "UPLOAD_ALREADY_COMPLETED") {
      continue;
    }

    failed.push({
      mediaId: failure.mediaId,
      fileName: issued.find((one) => one.mediaId === failure.mediaId)?.fileName ?? "",
      code: failure.code,
      message: failure.message,
    });
  }

  return { registered, failed, rejected };
};
