import type { QueryClient } from "@tanstack/react-query";

import { photosQueryKey, type MediaList } from "@/entities/media";

interface Params {
  queryClient: QueryClient;
  roomId: number;
  userId: number;
  mediaIds: number[];
}

export const updateMediaDeletedCache = ({ queryClient, roomId, userId, mediaIds }: Params) => {
  const deletedIds = new Set(mediaIds);

  queryClient.setQueryData<MediaList>(photosQueryKey(roomId, userId), (current) =>
    current
      ? { ...current, items: current.items.filter((item) => !deletedIds.has(item.mediaId)) }
      : current,
  );
};
