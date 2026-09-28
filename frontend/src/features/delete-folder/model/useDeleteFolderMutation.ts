import { useMutation, useQueryClient } from "@tanstack/react-query";

import { photosQueryKey, type MediaList } from "@/entities/media";
import { roomQueryKey, type Room } from "@/entities/room";
import { deleteFolder } from "../api/deleteFolder";

interface UseDeleteFolderMutationParams {
  roomId: number;
  roomCode: string;
  userId: number;
  folderId: number;
  accessToken: string;
  onError: () => void;
}

export const useDeleteFolderMutation = ({
  roomId,
  roomCode,
  userId,
  folderId,
  accessToken,
  onError,
}: UseDeleteFolderMutationParams) => {
  const queryClient = useQueryClient();
  const roomKey = roomQueryKey(roomCode, userId);
  const photosKey = photosQueryKey(roomId, userId);

  return useMutation({
    mutationFn: () => deleteFolder({ roomId, folderId, accessToken }),
    onMutate: async () => {
      await Promise.all([
        queryClient.cancelQueries({ queryKey: roomKey, exact: true }),
        queryClient.cancelQueries({ queryKey: photosKey, exact: true }),
      ]);

      const previousRoom = queryClient.getQueryData<Room>(roomKey);
      const previousPhotos = queryClient.getQueryData<MediaList>(photosKey);

      queryClient.setQueryData<Room>(roomKey, (current) =>
        current
          ? { ...current, folders: current.folders.filter((folder) => folder.id !== folderId) }
          : current,
      );
      queryClient.setQueryData<MediaList>(photosKey, (current) =>
        current
          ? {
              ...current,
              items: current.items.map((item) =>
                item.folderIds.includes(folderId)
                  ? { ...item, folderIds: item.folderIds.filter((id) => id !== folderId) }
                  : item,
              ),
            }
          : current,
      );

      return { previousRoom, previousPhotos };
    },
    onError: (_error, _variables, context) => {
      if (context?.previousRoom) queryClient.setQueryData(roomKey, context.previousRoom);
      if (context?.previousPhotos) queryClient.setQueryData(photosKey, context.previousPhotos);
      onError();
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: roomKey, exact: true });
      void queryClient.invalidateQueries({ queryKey: photosKey, exact: true });
    },
  });
};
