/**
 * 서버 `MediaType`(backend/src/main/java/com/sssok/domain/file/MediaType.java) 과 같은 규칙이다.
 * 여기서 통과시킨 파일이 발급에서 다시 거절되면 사용자는 같은 파일을 두 번 거절당한다.
 * 둘 중 하나를 고칠 때는 반드시 같이 고친다.
 */

const IMAGE_EXTENSIONS: readonly string[] = ["jpg", "jpeg", "png", "gif"];
const VIDEO_EXTENSIONS: readonly string[] = ["mp4", "webm", "mov"];

export const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
export const MAX_VIDEO_BYTES = 1024 * 1024 * 1024;

/**
 * 사진 선택기에 넘길 필터. 선택기를 좁혀 보여줄 뿐 강제력이 없어서,
 * 여기를 어떻게 두든 `selectMediaFiles` 의 검증은 그대로 필요하다.
 */
export const MEDIA_FILE_ACCEPT = "image/*,video/*";

export type MediaKind = "IMAGE" | "VIDEO";

/**
 * 확장자만 본다. `File.type` 은 믿을 수 없다 — 아이폰 사파리는 파일명이 `.HEIC` 인데
 * `image/jpeg` 를 주거나 아예 빈 문자열을 준다.
 */
export const mediaKindOf = (fileName: string): MediaKind | null => {
  const extension = extensionOf(fileName);

  if (extension === null) return null;
  if (IMAGE_EXTENSIONS.includes(extension)) return "IMAGE";
  if (VIDEO_EXTENSIONS.includes(extension)) return "VIDEO";

  return null;
};

export const maxBytesOf = (kind: MediaKind) =>
  kind === "IMAGE" ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;

const extensionOf = (fileName: string): string | null => {
  const dotIndex = fileName.lastIndexOf(".");

  if (dotIndex === -1) return null;

  return fileName
    .slice(dotIndex + 1)
    .trim()
    .toLowerCase();
};
