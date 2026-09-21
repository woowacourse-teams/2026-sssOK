import { useContext, useState } from "react";

import { PhotoSelectionContext } from "./PhotoSelectionContext";
import { emptySelection, type SelectionState } from "./types";

export const usePhotoSelection = (
  visiblePhotoIds: number[],
  totalCount = visiblePhotoIds.length,
) => {
  const shared = useContext(PhotoSelectionContext);
  // 단독 사용(스토리·단위 테스트)은 로컬 상태로 동작한다.
  const [localSelection, setLocalSelection] = useState<SelectionState>(emptySelection);
  const selection = shared?.selection ?? localSelection;
  const setSelection = shared?.setSelection ?? setLocalSelection;
  const isSelected = (photoId: number) =>
    selection.mode === "include"
      ? selection.includedIds.has(photoId)
      : !selection.excludedIds.has(photoId);
  const selectedPhotoIds = visiblePhotoIds.filter(isSelected);
  const selectedCount =
    selection.mode === "include"
      ? selection.includedIds.size
      : Math.max(0, totalCount - selection.excludedIds.size);
  const isAllSelected =
    totalCount > 0 && selection.mode === "exclude" && selection.excludedIds.size === 0;

  const togglePhoto = (photoId: number) =>
    setSelection((current) => {
      if (current.mode === "include") {
        const includedIds = new Set(current.includedIds);
        if (includedIds.has(photoId)) includedIds.delete(photoId);
        else includedIds.add(photoId);
        return { mode: "include", includedIds };
      }

      const excludedIds = new Set(current.excludedIds);
      if (excludedIds.has(photoId)) excludedIds.delete(photoId);
      else excludedIds.add(photoId);
      return { mode: "exclude", excludedIds };
    });
  const removePhoto = (photoId: number) => {
    if (isSelected(photoId)) togglePhoto(photoId);
  };
  const forgetPhotos = (photoIds: number[]) =>
    setSelection((current) => {
      const ids = current.mode === "include" ? current.includedIds : current.excludedIds;
      const nextIds = new Set(ids);
      photoIds.forEach((photoId) => nextIds.delete(photoId));

      return current.mode === "include"
        ? { mode: "include", includedIds: nextIds }
        : { mode: "exclude", excludedIds: nextIds };
    });
  const toggleAllPhotos = () =>
    setSelection(
      isAllSelected
        ? emptySelection()
        : {
            mode: "exclude",
            excludedIds: new Set(),
          },
    );
  const clearSelection = () => setSelection(emptySelection());

  return {
    selection,
    selectedPhotoIds,
    selectedCount,
    isAllSelected,
    togglePhoto,
    removePhoto,
    forgetPhotos,
    toggleAllPhotos,
    clearSelection,
  };
};
