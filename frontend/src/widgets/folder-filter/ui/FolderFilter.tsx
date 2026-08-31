import { useEffect, useRef } from "react";
import { HiPlus } from "react-icons/hi2";

import type { RoomFolder } from "@/entities/room";
import { IconButton } from "@/shared/ui/icon-button";
import {
  AddFolderButtonItem,
  Count,
  FolderButton,
  FolderList,
  FolderScrollArea,
} from "./FolderFilter.styles";

interface FolderFilterProps {
  totalCount: number;
  folders: RoomFolder[];
  selectedFolderId: number | null;
  onSelectFolder: (folderId: number | null) => void;
  onAddFolder?: () => void;
}

export const FolderFilter = ({
  totalCount,
  folders,
  selectedFolderId,
  onSelectFolder,
  onAddFolder,
}: FolderFilterProps) => {
  const folderListRef = useRef<HTMLDivElement>(null);
  const previousFolderCountRef = useRef(folders.length);

  useEffect(() => {
    const folderList = folderListRef.current;
    const hasNewFolder = folders.length > previousFolderCountRef.current;

    previousFolderCountRef.current = folders.length;

    if (!folderList || !hasNewFolder) return;

    const frameId = requestAnimationFrame(() => {
      folderList.scrollTo({ left: folderList.scrollWidth, behavior: "smooth" });
    });

    return () => cancelAnimationFrame(frameId);
  }, [folders.length]);

  return (
    <FolderScrollArea>
      <FolderList ref={folderListRef}>
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
        <AddFolderButtonItem>
          <IconButton type="button" size="sm" aria-label="폴더 추가" onClick={onAddFolder}>
            <HiPlus />
          </IconButton>
        </AddFolderButtonItem>
      </FolderList>
    </FolderScrollArea>
  );
};
