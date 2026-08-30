import { useMemo } from "react";

import { usePhotosQuery } from "@/entities/media";
import type { PhotoFilter } from "@/entities/media";

interface UseGalleryPhotosParams {
  roomId: number;
  accessToken: string;
  userId: number;
  selectedFolderId: number | null;
  selectedOption: PhotoFilter;
}

export const useGalleryPhotos = ({
  roomId,
  accessToken,
  userId,
  selectedFolderId,
  selectedOption,
}: UseGalleryPhotosParams) => {
  const photosQuery = usePhotosQuery({
    roomId,
    token: accessToken,
    userId,
  });

  const photos = useMemo(() => {
    // 썸네일 생성 전(null·빈 경로)인 항목은 카드와 선택 대상에서 제외한다.
    const allPhotos = (photosQuery.data?.items ?? []).filter(
      (photo) => typeof photo.thumbnailUrl === "string" && photo.thumbnailUrl.trim().length > 0,
    );
    const photosInFolder =
      selectedFolderId === null
        ? allPhotos
        : allPhotos.filter((photo) => photo.folderIds.includes(selectedFolderId));

    if (selectedOption === "mine") {
      return photosInFolder.filter((photo) => photo.uploaderId === userId);
    }

    if (selectedOption === "others") {
      return photosInFolder.filter((photo) => photo.uploaderId !== userId);
    }

    return photosInFolder;
  }, [photosQuery.data?.items, selectedFolderId, selectedOption, userId]);

  return {
    photos,
    isPending: photosQuery.isPending,
    isError: photosQuery.isError,
  };
};
