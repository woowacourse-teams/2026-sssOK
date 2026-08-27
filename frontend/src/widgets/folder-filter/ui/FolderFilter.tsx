import { HiPlus } from "react-icons/hi2";

import type { RoomFolder } from "@/entities/room";
import { IconButton } from "@/shared/ui/icon-button";
import { Count, FolderButton, FolderList } from "./FolderFilter.styles";

interface FolderFilterProps {
  totalCount: number;
  folders: RoomFolder[];
  selectedFolderId: number | null;
  onSelectFolder: (folderId: number | null) => void;
}

export const FolderFilter = ({
  totalCount,
  folders,
  selectedFolderId,
  onSelectFolder,
}: FolderFilterProps) => {
  return (
    <FolderList>
      <FolderButton
        type="button"
        $active={selectedFolderId === null}
        onClick={() => onSelectFolder(null)}
      >
        전체 <Count $active={selectedFolderId === null}>{totalCount}</Count>
      </FolderButton>

      {folders.map((folder) => {
        const isSelected = selectedFolderId === folder.id;

        return (
          <FolderButton
            key={folder.id}
            type="button"
            $active={isSelected}
            onClick={() => onSelectFolder(folder.id)}
          >
            {folder.name} <Count $active={isSelected}>{folder.photoCount}</Count>
          </FolderButton>
        );
      })}

      <IconButton type="button" size="sm" aria-label="폴더 추가">
        <HiPlus />
      </IconButton>
    </FolderList>
  );
};
