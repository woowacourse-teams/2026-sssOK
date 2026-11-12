import { useState } from "react";

import type { PhotoFilter, PhotoSort } from "@/entities/media";

export const useGalleryFilter = (): {
  selectedFolderId: number | null;
  selectedOption: PhotoFilter;
  selectedSort: PhotoSort;
  selectFolder: (folderId: number | null) => void;
  selectOption: (option: PhotoFilter) => void;
  selectSort: (sort: PhotoSort) => void;
} => {
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null);
  const [selectedOption, setSelectedOption] = useState<PhotoFilter>("all");
  const [selectedSort, setSelectedSort] = useState<PhotoSort>("upload");

  const selectFolder = (folderId: number | null): void => {
    setSelectedFolderId(folderId);
    setSelectedOption("all");
    setSelectedSort("upload");
  };

  const selectOption = (option: PhotoFilter): void => {
    setSelectedOption(option);
    setSelectedSort("upload");
  };

  const selectSort = (sort: PhotoSort): void => setSelectedSort(sort);

  return {
    selectedFolderId,
    selectedOption,
    selectedSort,
    selectFolder,
    selectOption,
    selectSort,
  };
};
