import { useContext, useState } from "react";

import { PhotoSelectionContext } from "./PhotoSelectionContext";

export const usePhotoSelection = (visiblePhotoIds: number[]) => {
  const shared = useContext(PhotoSelectionContext);
  // 단독 사용(스토리·단위 테스트)은 로컬 상태로 동작한다.
  const [localIds, setLocalIds] = useState<number[]>([]);
  const selectedPhotoIds = shared?.selectedPhotoIds ?? localIds;
  const setSelectedPhotoIds = shared?.setSelectedPhotoIds ?? setLocalIds;
  const isAllSelected =
    visiblePhotoIds.length > 0 && visiblePhotoIds.every((id) => selectedPhotoIds.includes(id));

  const togglePhoto = (photoId: number) =>
    setSelectedPhotoIds((current) =>
      current.includes(photoId) ? current.filter((id) => id !== photoId) : [...current, photoId],
    );
  const removePhoto = (photoId: number) =>
    setSelectedPhotoIds((current) => current.filter((id) => id !== photoId));
  const toggleAllPhotos = () => setSelectedPhotoIds(isAllSelected ? [] : visiblePhotoIds);
  const clearSelection = () => setSelectedPhotoIds([]);

  return {
    selectedPhotoIds,
    isAllSelected,
    togglePhoto,
    removePhoto,
    toggleAllPhotos,
    clearSelection,
  };
};
