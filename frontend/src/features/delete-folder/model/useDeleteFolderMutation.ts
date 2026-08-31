import { useMutation } from "@tanstack/react-query";

import { deleteFolder } from "../api/deleteFolder";

interface UseDeleteFolderMutationParams {
  roomId: number;
  folderId: number;
  accessToken: string;
  onSuccess: () => void | Promise<void>;
}

export const useDeleteFolderMutation = ({
  roomId,
  folderId,
  accessToken,
  onSuccess,
}: UseDeleteFolderMutationParams) =>
  useMutation({
    mutationFn: () => deleteFolder({ roomId, folderId, accessToken }),
    onSuccess,
  });
