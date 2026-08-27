import { formatBytes } from "../lib/formatBytes";
import { MAX_IMAGE_BYTES, MAX_VIDEO_BYTES, maxBytesOf, mediaKindOf } from "../lib/mediaFileRules";

/**
 * 발급이 내려주는 `rejected` 코드와 같은 값을 쓴다.
 * 화면은 "발급 전에 걸러진 것" 과 "발급이 거절한 것" 을 구분해 보여줄 이유가 없다 —
 * 둘 다 다시 눌러도 똑같이 실패하는, 재시도가 의미 없는 파일이다.
 */
export type SelectionRejectionCode = "UNSUPPORTED_FILE_TYPE" | "FILE_SIZE_EXCEEDED";

export interface RejectedSelection {
  fileName: string;
  /** 실제 크기. 한도를 얼마나 넘었는지 화면이 보여줘야 사용자가 무엇을 뺄지 안다. */
  size: number;
  code: SelectionRejectionCode;
  message: string;
}

export interface MediaSelection {
  /** 발급 요청에 실을 파일. 고른 것 전부가 아니다. */
  accepted: File[];
  rejected: RejectedSelection[];
}

/**
 * 사유 문구는 시안(07d)을 따른다. **한도가 아니라 "넘었다"를 말한다** —
 * 한도 자체는 모달 아래 칩이 항상 보여주고 있어서, 파일마다 반복하면 같은 말이 두 번이다.
 */
const UNSUPPORTED_MESSAGE = "이미지와 영상만 올릴 수 있어요";
const OVERSIZED_MESSAGE: Record<"IMAGE" | "VIDEO", string> = {
  IMAGE: `이미지 최대 ${formatBytes(MAX_IMAGE_BYTES)} 초과`,
  VIDEO: `영상 최대 ${formatBytes(MAX_VIDEO_BYTES)} 초과`,
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
        size: file.size,
        code: "UNSUPPORTED_FILE_TYPE",
        message: UNSUPPORTED_MESSAGE,
      });
      return;
    }

    if (file.size > maxBytesOf(kind)) {
      rejected.push({
        fileName: file.name,
        size: file.size,
        code: "FILE_SIZE_EXCEEDED",
        message: OVERSIZED_MESSAGE[kind],
      });
      return;
    }

    accepted.push(file);
  });

  return { accepted, rejected };
};
