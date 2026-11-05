import type { GalleryItem } from "@/entities/media";
import { ORGANIZE_PHOTO_LIMIT } from "../config";

/**
 * 서버가 정리 대상으로 세는 사진 수.
 *
 * 서버는 동영상·GIF·처리 중인 사진을 빼고 센다(`excludedCount`). 갤러리 전체 장수로 세면
 * 동영상이 섞인 방에서 한도를 넘었다고 잘못 막게 된다. 아직 올리는 중인 사진도 뺀다.
 */
export const countAnalyzablePhotos = (items: GalleryItem[]) =>
  items.filter(
    (item) =>
      item.type === "server" &&
      item.media.type === "IMAGE" &&
      item.media.status === "READY" &&
      item.media.mimeType !== "image/gif",
  ).length;

/** 정리 대상이 될 사진 파일인지. 동영상·GIF 는 서버가 빼므로 기다리지 않는다. */
const isPhotoFile = (mimeType: string) => mimeType.startsWith("image/") && mimeType !== "image/gif";

/** 아이폰은 파일 타입을 빈 문자열로 줄 때가 있어서, 그때는 확장자로 본다. */
const isLocalPhoto = (file: File) =>
  file.type ? isPhotoFile(file.type) : /\.(jpe?g|png|heic|heif)$/i.test(file.name);

/**
 * 아직 올리는 중이거나 서버가 처리하고 있는 사진이 있는지.
 *
 * 처리가 끝나기 전에 정리를 시작하면 그 사진이 빠지거나 서버가 거절한다. 그래서 모두 READY 가
 * 될 때까지 정리를 막는다. 처리에 실패한(FAILED) 사진은 다시 READY 가 되지 않으니 기다리지 않는다.
 *
 * 갤러리는 썸네일이 없는 서버 항목을 그리지 않으므로, 그린 목록이 아니라 서버 목록 원본과
 * 올리는 중인 자리를 함께 넘겨야 한다.
 */
export const hasProcessingPhotos = (items: GalleryItem[]) =>
  items.some((item) =>
    item.type === "local"
      ? isLocalPhoto(item.file)
      : item.media.type === "IMAGE" &&
        isPhotoFile(item.media.mimeType) &&
        (item.media.status === "RESERVED" || item.media.status === "PROCESSING"),
  );

/**
 * 정리를 시작할 수 없는 까닭. 모달로 알린다.
 * 장수 문제는 시작 전에 막고, 체험 소진(`trialExhausted`)은 서버가 거절했을 때만 온다.
 */
export type OrganizeLimit =
  { reason: "tooMany"; count: number | null } | { reason: "empty" } | { reason: "trialExhausted" };

/** 시작 전에 막아야 하면 그 까닭을, 아니면 null 을 돌려준다. */
export const organizeLimitOf = (photoCount: number): OrganizeLimit | null => {
  if (photoCount === 0) return { reason: "empty" };
  if (photoCount > ORGANIZE_PHOTO_LIMIT) return { reason: "tooMany", count: photoCount };
  return null;
};
