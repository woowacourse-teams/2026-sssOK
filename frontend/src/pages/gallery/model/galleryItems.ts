import type { GalleryItem, MediaItem, PhotoFilter, PhotoSort } from "@/entities/media";

export const mergeGalleryItems = (photos: MediaItem[], uploadSlots: GalleryItem[]) => {
  const itemsById = new Map<number, GalleryItem>();

  for (const slot of uploadSlots) {
    itemsById.set(slot.mediaId, slot);
  }

  for (const media of photos) {
    if (!media.thumbnailUrl?.trim()) {
      const preview = itemsById.get(media.mediaId);

      if (preview) {
        itemsById.set(media.mediaId, { ...preview, folderIds: media.folderIds });
      }
      continue;
    }

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
    if (option === "liked") return item.type === "server" && item.media.likedByMe;
    return true;
  });

export const sortGalleryItems = (items: GalleryItem[], sort: PhotoSort) => {
  if (sort === "upload") return items;

  return [...items].sort((left, right) => {
    const leftLikeCount = left.type === "server" ? (left.media.likeCount ?? 0) : 0;
    const rightLikeCount = right.type === "server" ? (right.media.likeCount ?? 0) : 0;

    return rightLikeCount - leftLikeCount;
  });
};
