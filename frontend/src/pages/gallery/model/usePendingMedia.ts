import { useState } from "react";

import type { GalleryItem, MediaItem } from "@/entities/media";
import type { PendingMedia } from "@/features/upload-media";

export const usePendingMedia = () => {
  const [uploadSlots, setUploadSlots] = useState<GalleryItem[]>([]);

  const addPendingMedia = ({ mediaId, file, folderIds }: PendingMedia) => {
    setUploadSlots((current) => [
      { mediaId, type: "local", file, folderIds },
      ...current.filter((item) => item.mediaId !== mediaId),
    ]);
  };

  const removePendingMedia = (mediaIds: number[]) => {
    setUploadSlots((current) => current.filter((item) => !mediaIds.includes(item.mediaId)));
  };

  const replacePendingMedia = (media: MediaItem) => {
    const hasPendingMedia = uploadSlots.some((slot) => slot.mediaId === media.mediaId);
    setUploadSlots((current) =>
      current.map((slot) => {
        return slot.mediaId !== media.mediaId
          ? slot
          : { mediaId: media.mediaId, type: "server", media, folderIds: media.folderIds };
      }),
    );
    return hasPendingMedia;
  };

  return {
    uploadSlots,
    addPendingMedia,
    removePendingMedia,
    replacePendingMedia,
  };
};
