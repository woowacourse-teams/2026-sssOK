import { useMutation, useQueryClient } from "@tanstack/react-query";

import { photosQueryKey, type MediaList } from "@/entities/media";
import { addMediaLike } from "../api/addMediaLike";
import { removeMediaLike } from "../api/removeMediaLike";
import { setMyMediaLike } from "./mediaLikeCache";

interface UseMediaLikeParams {
  roomId: number;
  mediaId: number;
  userId: number;
  token: string;
}

export const useMediaLike = ({ roomId, mediaId, userId, token }: UseMediaLikeParams) => {
  const queryClient = useQueryClient();
  const photosKey = photosQueryKey(roomId, userId);
  const updatePhotos = (update: (current: MediaList | undefined) => MediaList | undefined) =>
    queryClient.setQueryData<MediaList>(photosKey, update);

  return useMutation({
    mutationFn: (shouldLike: boolean) =>
      shouldLike
        ? addMediaLike({ roomId, mediaId, token })
        : removeMediaLike({ roomId, mediaId, token }),
    onMutate: async (shouldLike) => {
      await queryClient.cancelQueries({ queryKey: photosKey, exact: true });

      updatePhotos((current) => setMyMediaLike(current, mediaId, shouldLike));
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: photosKey, exact: true });
    },
  });
};
