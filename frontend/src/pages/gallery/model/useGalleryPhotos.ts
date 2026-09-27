import { useMemo } from "react";

import { usePhotosQuery } from "@/entities/media";
import type { GalleryItem, PhotoFilter } from "@/entities/media";
import { countItemsByFolder, filterGalleryItems, mergeGalleryItems } from "./galleryItems";

const EMPTY_UPLOAD_SLOTS: GalleryItem[] = [];

interface UseGalleryPhotosParams {
  roomId: number;
  accessToken: string;
  userId: number;
  selectedFolderId: number | null;
  selectedOption: PhotoFilter;
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
  const folderCounts = useMemo(() => countItemsByFolder(allGalleryItems), [allGalleryItems]);
  const visibleItems = useMemo(
    () => filterGalleryItems(allGalleryItems, selectedFolderId, selectedOption, userId),
    [allGalleryItems, selectedFolderId, selectedOption, userId],
  );

  // 업로드한 파일은 READY가 되어도 같은 카드 안에서 썸네일만 교체한다.
  // 서버 목록에도 들어온 파일이라면 최신 서버 데이터로 미리보기 자리를 갱신한다.
  const visibleItemsById = new Map(visibleItems.map((item) => [item.mediaId, item]));
  const visibleUploadSlots = uploadSlots.flatMap((slot) => {
    const visibleItem = visibleItemsById.get(slot.mediaId);
    return visibleItem ? [visibleItem] : [];
  });
  const uploadSlotIds = new Set(uploadSlots.map((slot) => slot.mediaId));
  const serverItemsWithoutUploadSlot = visibleItems.filter(
    (item) => !uploadSlotIds.has(item.mediaId),
  );
  const photos = serverItemsWithoutUploadSlot.flatMap((item) =>
    item.type === "server" ? [item.media] : [],
  );
  const galleryItems = [...visibleUploadSlots, ...serverItemsWithoutUploadSlot];

  return {
    photos,
    visibleUploadSlots,
    galleryItems,
    totalCount: allGalleryItems.length,
    folderCounts,
    isPending: photosQuery.isPending,
    isError: photosQuery.isError,
  };
};
