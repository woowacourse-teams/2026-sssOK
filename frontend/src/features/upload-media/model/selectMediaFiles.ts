import { MAX_IMAGE_BYTES, MAX_VIDEO_BYTES, maxBytesOf, mediaKindOf } from "../lib/mediaFileRules";

/**
 * 발급이 내려주는 `rejected` 코드와 같은 값을 쓴다.
 * 화면은 "발급 전에 걸러진 것" 과 "발급이 거절한 것" 을 구분해 보여줄 이유가 없다 —
 * 둘 다 다시 눌러도 똑같이 실패하는, 재시도가 의미 없는 파일이다.
 */
export type SelectionRejectionCode = "UNSUPPORTED_FILE_TYPE" | "FILE_SIZE_EXCEEDED";

export interface RejectedSelection {
  fileName: string;
  code: SelectionRejectionCode;
  message: string;
}

export interface MediaSelection {
  /** 발급 요청에 실을 파일. 고른 것 전부가 아니다. */
  accepted: File[];
  rejected: RejectedSelection[];
}

const UNSUPPORTED_MESSAGE = "이미지와 영상만 올릴 수 있어요";
const OVERSIZED_MESSAGE: Record<"IMAGE" | "VIDEO", string> = {
  IMAGE: `사진은 ${MAX_IMAGE_BYTES / 1024 / 1024}MB까지 올릴 수 있어요`,
  VIDEO: `영상은 ${MAX_VIDEO_BYTES / 1024 / 1024 / 1024}GB까지 올릴 수 있어요`,
};

/**
 * 고른 파일을 올릴 수 있는 것과 없는 것으로 가른다.
 *
 * 서버가 어차피 같은 검사를 하지만 여기서 먼저 거른다 — 1GB 짜리를 다 올린 뒤에
 * 거절당하면 사용자는 회선을 통째로 버린 셈이 된다.
 */
export const selectMediaFiles = (files: File[]): MediaSelection => {
  const accepted: File[] = [];
  const rejected: RejectedSelection[] = [];

  files.forEach((file) => {
    const kind = mediaKindOf(file.name);

    if (kind === null) {
      rejected.push({
        fileName: file.name,
        code: "UNSUPPORTED_FILE_TYPE",
        message: UNSUPPORTED_MESSAGE,
      });
      return;
    }

    if (file.size > maxBytesOf(kind)) {
      rejected.push({
        fileName: file.name,
        code: "FILE_SIZE_EXCEEDED",
        message: OVERSIZED_MESSAGE[kind],
      });
      return;
    }

    accepted.push(file);
  });

  return { accepted, rejected };
};
