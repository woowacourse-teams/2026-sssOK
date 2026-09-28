import { useMemo } from "react";

import { usePhotosQuery } from "@/entities/media";
import type { GalleryItem, PhotoFilter } from "@/entities/media";
import type { RoomFolder } from "@/entities/room";
import { countItemsByFolder, filterGalleryItems, mergeGalleryItems } from "./galleryItems";

const EMPTY_UPLOAD_SLOTS: GalleryItem[] = [];

interface UseGalleryPhotosParams {
  roomId: number;
  accessToken: string;
  userId: number;
  selectedFolderId: number | null;
  selectedOption: PhotoFilter;
  initialTotalCount: number;
  initialFolders: RoomFolder[];
  uploadSlots?: GalleryItem[];
}

/**
 * 갤러리에 그릴 사진. 방 전체를 한 번 받아 두고 폴더·업로더는 여기서 거른다.
 * 옵션을 바꿔도 다시 부르지 않고, 거른 결과가 곧 전체 선택의 대상이다.
 */
export const useGalleryPhotos = ({
  roomId,
  accessToken,
  userId,
  selectedFolderId,
  selectedOption,
  initialTotalCount,
  initialFolders,
  uploadSlots = EMPTY_UPLOAD_SLOTS,
}: UseGalleryPhotosParams) => {
  const photosQuery = usePhotosQuery({
    roomId,
    token: accessToken,
    userId,
  });

  const allGalleryItems = useMemo(
    () => mergeGalleryItems(photosQuery.data?.items ?? [], uploadSlots),
    [photosQuery.data?.items, uploadSlots],
  );
  const completedUploadIds = useMemo(() => {
    const serverMediaIds = new Set((photosQuery.data?.items ?? []).map((item) => item.mediaId));
    return uploadSlots
      .filter((slot) => serverMediaIds.has(slot.mediaId))
      .map((slot) => slot.mediaId);
  }, [photosQuery.data?.items, uploadSlots]);
  const hasLoadedPhotos = photosQuery.data !== undefined;
  const folderCounts = useMemo(
    () =>
      hasLoadedPhotos
        ? countItemsByFolder(allGalleryItems)
        : new Map(initialFolders.map((folder) => [folder.id, folder.photoCount])),
    [allGalleryItems, hasLoadedPhotos, initialFolders],
  );
  const visibleItems = useMemo(
    () => filterGalleryItems(allGalleryItems, selectedFolderId, selectedOption, userId),
    [allGalleryItems, selectedFolderId, selectedOption, userId],
  );

  return {
    galleryItems: visibleItems,
    completedUploadIds,
    totalCount: hasLoadedPhotos ? allGalleryItems.length : initialTotalCount,
    folderCounts,
    isPending: photosQuery.isPending,
    isError: photosQuery.isError,
  };
};
