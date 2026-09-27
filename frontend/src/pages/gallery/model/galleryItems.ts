import type { GalleryItem, MediaItem, PhotoFilter } from "@/entities/media";

export const mergeGalleryItems = (photos: MediaItem[], uploadSlots: GalleryItem[]) => {
  const itemsById = new Map<number, GalleryItem>();

  for (const slot of uploadSlots) {
    itemsById.set(slot.mediaId, slot);
  }

  for (const media of photos) {
    if (!media.thumbnailUrl?.trim()) continue;

    // 같은 파일은 한 번만 표시한다. 재조회된 서버의 폴더 정보가 미리보기보다 우선한다.
    itemsById.set(media.mediaId, {
      type: "server",
      mediaId: media.mediaId,
      media,
      folderIds: media.folderIds,
    });
  }

  return [...itemsById.values()];
};

export const countItemsByFolder = (items: GalleryItem[]) => {
  const counts = new Map<number, number>();

  for (const item of items) {
    for (const folderId of new Set(item.folderIds)) {
      counts.set(folderId, (counts.get(folderId) ?? 0) + 1);
    }
  }

  return counts;
};

export const filterGalleryItems = (
  items: GalleryItem[],
  folderId: number | null,
  option: PhotoFilter,
  userId: number,
) =>
  items.filter((item) => {
    if (folderId !== null && !item.folderIds.includes(folderId)) return false;

    const isMine = item.type === "local" || item.media.uploaderId === userId;
    if (option === "mine") return isMine;
    if (option === "others") return !isMine;
    return true;
  });
