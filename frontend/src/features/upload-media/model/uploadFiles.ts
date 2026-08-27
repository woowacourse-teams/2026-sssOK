import { issueUploadUrls } from "../api/issueUploadUrls";
import { registerMedia } from "../api/registerMedia";
import type { Media } from "@/entities/media";
import { UPLOAD_CONCURRENCY } from "../config";
import { runWithLimit } from "@/shared/lib";
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
  onStarted,
  onUploaded,
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

  const targets = pairWithFiles(files, issued, rejected);

  // 거절분이 빠진 뒤라야 "몇 장 중 몇 장" 의 분모가 맞는다. 첫 PUT 보다 먼저 알린다 (#73).
  onStarted?.(
    targets.map(({ issued: one, file }) => ({
      mediaId: one.mediaId,
      fileName: one.fileName,
      size: file.size,
    })),
  );

  const results = await runWithLimit(targets, UPLOAD_CONCURRENCY, async (target) => {
    const result = await uploadOne({
      roomId,
      token,
      issued: target.issued,
      file: target.file,
      signal,
      onProgress,
    });

    // 등록까지 기다리면 마지막 한 번에 몰아서 오른다. 올라간 즉시 세는 게 사용자가 보는 진행이다.
    if (result.ok) {
      onUploaded?.({ mediaId: target.issued.mediaId, fileName: target.issued.fileName });
    }

    return result;
  });

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
    return { registered: [], failed, rejected, alreadyRegistered: 0 };
  }

  const registerResult = await registerMedia(roomId, { mediaIds: uploadedIds }, token);
  const registered: Media[] = registerResult.registered;

  const targetByMediaId = new Map(targets.map((target) => [target.issued.mediaId, target]));
  let alreadyRegistered = 0;

  for (const failure of registerResult.failed) {
    // 이미 올라간 것이다. 실패로 보여주면 멀쩡히 올라간 사진을 실패로 보게 된다.
    // 응답에 Media 가 없어서 registered 에는 못 넣는다 — 갤러리를 다시 불러오면 나타난다.
    if (failure.code === "UPLOAD_ALREADY_COMPLETED") {
      alreadyRegistered += 1;
      continue;
    }

    const target = targetByMediaId.get(failure.mediaId);

    // 우리가 발급받아 올린 mediaId 만 등록에 실었으므로 못 찾을 수 없다.
    // 그래도 응답이 어긋나면 빈 파일을 지어내지 않는다 — 재시도가 0바이트를 올리게 된다.
    if (target === undefined) {
      continue;
    }

    failed.push({
      mediaId: failure.mediaId,
      fileName: target.issued.fileName,
      code: failure.code,
      message: failure.message,
      file: target.file,
    });
  }

  return { registered, failed, rejected, alreadyRegistered };
};
