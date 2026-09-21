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
}: UseCreateFolderActionParams) => {
  const queryClient = useQueryClient();
  const { openModal } = useGalleryModal();

  const handleCreateFolder = async (source: CreateFolderSource) => {
    track("Folder Create Started", { source });

    const folder = await openModal({ type: "create-folder" });
    if (!folder) return;

    track("Folder Created", { source });

    await queryClient.invalidateQueries({
      queryKey: roomQueryKey(roomCode, userId),
      exact: true,
    });

    selectFolder(folder.id);
    clearSelection();
  };

  return { handleCreateFolder };
};
