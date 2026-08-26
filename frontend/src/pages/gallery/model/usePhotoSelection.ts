import { useState } from "react";

export const usePhotoSelection = (visiblePhotoIds: number[]) => {
  const [selectedPhotoIds, setSelectedPhotoIds] = useState<number[]>([]);

  const isAllSelected =
    visiblePhotoIds.length > 0 && visiblePhotoIds.every((id) => selectedPhotoIds.includes(id));

  const togglePhoto = (photoId: number) => {
    setSelectedPhotoIds((current) =>
      current.includes(photoId) ? current.filter((id) => id !== photoId) : [...current, photoId],
    );
  };

  const toggleAllPhotos = () => {
    setSelectedPhotoIds(isAllSelected ? [] : visiblePhotoIds);
  };

  const clearSelection = () => {
    setSelectedPhotoIds([]);
  };

  return {
    selectedPhotoIds,
    isAllSelected,
    togglePhoto,
    toggleAllPhotos,
    clearSelection,
  };
};
