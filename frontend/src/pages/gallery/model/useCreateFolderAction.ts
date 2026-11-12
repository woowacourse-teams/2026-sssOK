import type { RoomFolder } from "@/entities/room/model/types";
import { useQueryClient } from "@tanstack/react-query";

import { roomQueryKey } from "@/entities/room";
import { track } from "@/shared/lib";
import { useGalleryModal } from "./galleryModalContext";

/** 폴더 추가 진입점. 메뉴 안 항목과 폴더 필터 옆 + 버튼 중 어느 쪽을 쓰는지 본다. */
export type CreateFolderSource = "menu" | "filter_plus";

interface UseCreateFolderActionParams {
  roomCode: string;
  userId: number;
  selectFolder: (folderId: number | null) => void;
  clearSelection: () => void;
}

export const useCreateFolderAction = ({
  roomCode,
  userId,
  selectFolder,
  clearSelection,
}: UseCreateFolderActionParams): {
  handleCreateFolder: (source: CreateFolderSource) => Promise<void>;
  requestCreateFolder: () => Promise<RoomFolder | null>;
} => {
  const queryClient = useQueryClient();
  const { openModal } = useGalleryModal();

  const requestCreateFolder = async (): Promise<RoomFolder | null> => {
    const folder = await openModal({ type: "create-folder" });
    if (!folder) return null;
    await queryClient.invalidateQueries(
      { queryKey: roomQueryKey(roomCode, userId), exact: true },
      { cancelRefetch: false },
    );
    return folder;
  };

  const handleCreateFolder = async (source: CreateFolderSource): Promise<void> => {
    track("Folder Create Started", { source });
    const folder = await requestCreateFolder();
    if (!folder) return;

    track("Folder Created", { source });
    selectFolder(folder.id);
    clearSelection();
  };

  return { handleCreateFolder, requestCreateFolder };
};
