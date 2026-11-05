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
