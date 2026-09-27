import { useContext, useState } from "react";

import { PhotoSelectionContext } from "./PhotoSelectionContext";

/**
 * 고른 사진의 id 만 들고 있는다. 서버에도 이 id 그대로 보낸다.
 */
export const usePhotoSelection = (visiblePhotoIds: number[]) => {
  const shared = useContext(PhotoSelectionContext);
  // 단독 사용(스토리·단위 테스트)은 로컬 상태로 동작한다.
  const [localIds, setLocalIds] = useState<ReadonlySet<number>>(() => new Set());
  const selectedIds = shared?.selectedIds ?? localIds;
  const setSelectedIds = shared?.setSelectedIds ?? setLocalIds;

  // 고른 순서가 아니라 화면 순서다. 다운로드 파일 차례가 갤러리와 같아야 한다.
  const selectedPhotoIds = visiblePhotoIds.filter((id) => selectedIds.has(id));
  const isAllSelected =
    visiblePhotoIds.length > 0 && selectedPhotoIds.length === visiblePhotoIds.length;

  const togglePhoto = (photoId: number) =>
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(photoId)) next.delete(photoId);
      else next.add(photoId);
      return next;
    });
  const removePhotos = (photoIds: number[]) =>
    setSelectedIds((current) => {
      if (!photoIds.some((id) => current.has(id))) return current;
      const next = new Set(current);
      photoIds.forEach((id) => next.delete(id));
      return next;
    });
  const toggleAllPhotos = () =>
    setSelectedIds(isAllSelected ? new Set() : new Set(visiblePhotoIds));
  const clearSelection = () => setSelectedIds(new Set());

  return {
    selectedPhotoIds,
    isAllSelected,
    togglePhoto,
    removePhotos,
    toggleAllPhotos,
    clearSelection,
  };
};
