import { useState } from "react";

import type { PhotoFilter, PhotoSort } from "@/entities/media";

export const useGalleryFilter = () => {
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null);
  const [selectedOption, setSelectedOption] = useState<PhotoFilter>("all");
  const [selectedSort, setSelectedSort] = useState<PhotoSort>("upload");

  const selectFolder = (folderId: number | null) => {
    setSelectedFolderId(folderId);
    setSelectedOption("all");
    setSelectedSort("upload");
  };

  const selectOption = (option: PhotoFilter) => {
    setSelectedOption(option);
    setSelectedSort("upload");
  };

  const selectSort = (sort: PhotoSort) => setSelectedSort(sort);

  return {
    selectedFolderId,
    selectedOption,
    selectedSort,
    selectFolder,
    selectOption,
    selectSort,
  };
};
