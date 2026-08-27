import { useState } from "react";

import type { PhotoFilter } from "@/entities/media";

export const useGalleryFilter = () => {
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null);
  const [selectedOption, setSelectedOption] = useState<PhotoFilter>("all");

  const selectFolder = (folderId: number | null) => {
    setSelectedFolderId(folderId);
    setSelectedOption("all");
  };

  const selectOption = (option: PhotoFilter) => {
    setSelectedOption(option);
  };

  return {
    selectedFolderId,
    selectedOption,
    selectFolder,
    selectOption,
  };
};
