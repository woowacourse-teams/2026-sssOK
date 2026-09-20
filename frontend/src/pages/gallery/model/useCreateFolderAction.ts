import { useQueryClient } from "@tanstack/react-query";

import { roomQueryKey } from "@/entities/room";
import { useGalleryModal } from "./galleryModalContext";

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

  const handleCreateFolder = async () => {
    const folder = await openModal({ type: "create-folder" });
    if (!folder) return;

    await queryClient.invalidateQueries({
      queryKey: roomQueryKey(roomCode, userId),
      exact: true,
    });

    selectFolder(folder.id);
    clearSelection();
  };

  return { handleCreateFolder };
};
