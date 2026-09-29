import { useCallback, useState } from "react";

import type { GalleryItem, Media } from "@/entities/media";
import type { PendingMedia } from "@/features/upload-media";

export const usePendingMedia = () => {
  const [uploadSlots, setUploadSlots] = useState<GalleryItem[]>([]);

  const addPendingMedia = useCallback(({ mediaId, file, folderIds }: PendingMedia) => {
    setUploadSlots((current) => [
      { mediaId, type: "local", file, folderIds },
      ...current.filter((item) => item.mediaId !== mediaId),
    ]);
  }, []);

  const addProcessingMedia = useCallback((media: Media) => {
    setUploadSlots((current) => {
      if (current.some((item) => item.mediaId === media.mediaId)) return current;

      return [
        { mediaId: media.mediaId, type: "server", media, folderIds: media.folderIds },
        ...current,
      ];
    });
  }, []);

  const removePendingMedia = useCallback((mediaIds: number[]) => {
    setUploadSlots((current) => current.filter((item) => !mediaIds.includes(item.mediaId)));
  }, []);

  const addPendingMediaToFolder = useCallback((mediaIds: number[], folderId: number) => {
    setUploadSlots((current) =>
      current.map((item) =>
        mediaIds.includes(item.mediaId) && !item.folderIds.includes(folderId)
          ? { ...item, folderIds: [...item.folderIds, folderId] }
          : item,
      ),
    );
  }, []);

  const removePendingMediaFromFolder = useCallback((mediaIds: number[], folderId: number) => {
    setUploadSlots((current) =>
      current.map((item) =>
        mediaIds.includes(item.mediaId)
          ? { ...item, folderIds: item.folderIds.filter((id) => id !== folderId) }
          : item,
      ),
    );
  }, []);

  return {
    uploadSlots,
    addPendingMedia,
    addProcessingMedia,
    removePendingMedia,
    addPendingMediaToFolder,
    removePendingMediaFromFolder,
  };
};
