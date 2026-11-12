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

/** 탭 양옆에 남길 여백. 목록의 좌우 안쪽 여백과 같다. */
const REVEAL_GAP = 16;

/** 탭이 목록 밖으로 나가 있으면 보이게 하려고 옮길 거리. 이미 다 보이면 0 이다. */
const offsetToReveal = (list: HTMLElement, tab: HTMLElement): number => {
  const listRect = list.getBoundingClientRect();
  const tabRect = tab.getBoundingClientRect();

  if (tabRect.left < listRect.left) return tabRect.left - listRect.left - REVEAL_GAP;
  if (tabRect.right > listRect.right) return tabRect.right - listRect.right + REVEAL_GAP;
  return 0;
};

export const FolderFilter = ({
  totalCount,
  folders,
  selectedFolderId,
  onSelectFolder,
  onAddFolder,
}: FolderFilterProps) => {
  const folderListRef = useRef<HTMLDivElement>(null);
  const previousFolderCountRef = useRef(folders.length);

  // 선택한 탭이 가려져 있으면 보이게 옮긴다. 폴더가 늘었는데 고른 폴더가 없으면 새 폴더가 있는 끝으로 간다.
  // 폴더 목록을 늦게 받아도 받은 뒤 다시 맞춘다.
  useEffect(() => {
    const folderList = folderListRef.current;
    const hasNewFolder = folders.length > previousFolderCountRef.current;

    previousFolderCountRef.current = folders.length;

    if (!folderList) return;

    const frameId = requestAnimationFrame(() => {
      const selected =
        selectedFolderId === null
          ? null
          : folderList.querySelector<HTMLElement>('[aria-pressed="true"]');

      if (selected) {
        const offset = offsetToReveal(folderList, selected);
        if (offset !== 0) {
          folderList.scrollTo({ left: folderList.scrollLeft + offset, behavior: "smooth" });
        }
        return;
      }
      if (hasNewFolder) {
        folderList.scrollTo({ left: folderList.scrollWidth, behavior: "smooth" });
      }
    });

    return () => cancelAnimationFrame(frameId);
  }, [folders.length, selectedFolderId]);

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
