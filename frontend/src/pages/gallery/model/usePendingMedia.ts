import { useCallback, useState } from "react";

import type { GalleryItem } from "@/entities/media";
import type { PendingMedia } from "@/features/upload-media";

export const usePendingMedia = () => {
  const [uploadSlots, setUploadSlots] = useState<GalleryItem[]>([]);

  const addPendingMedia = useCallback(({ mediaId, file, folderIds }: PendingMedia) => {
    setUploadSlots((current) => [
      { mediaId, type: "local", file, folderIds },
      ...current.filter((item) => item.mediaId !== mediaId),
    ]);
  }, []);

  const removePendingMedia = useCallback((mediaIds: number[]) => {
    setUploadSlots((current) => current.filter((item) => !mediaIds.includes(item.mediaId)));
  }, []);

  return {
    uploadSlots,
    addPendingMedia,
    removePendingMedia,
  };
};
