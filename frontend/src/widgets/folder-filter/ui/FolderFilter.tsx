import { useEffect, useRef } from "react";
import { LuFolderPlus } from "react-icons/lu";

import type { RoomFolder } from "@/entities/room";
import {
  AddFolderButton,
  Count,
  FolderFilterBar,
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
    <FolderFilterBar>
      <FolderScrollArea>
        <FolderList ref={folderListRef}>
          <FolderButton
            type="button"
            $active={selectedFolderId === null}
            aria-label={`전체 사진 및 동영상, ${totalCount}개`}
            aria-pressed={selectedFolderId === null}
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
                aria-label={`${folder.name} 폴더, 사진 및 동영상 ${folder.photoCount}개`}
                aria-pressed={isSelected}
                onClick={() => onSelectFolder(folder.id)}
              >
                {folder.name} <Count $active={isSelected}>{folder.photoCount}</Count>
              </FolderButton>
            );
          })}
        </FolderList>
      </FolderScrollArea>
      <AddFolderButton type="button" onClick={onAddFolder}>
        <LuFolderPlus aria-hidden="true" />
        폴더 추가
      </AddFolderButton>
    </FolderFilterBar>
  );
};
