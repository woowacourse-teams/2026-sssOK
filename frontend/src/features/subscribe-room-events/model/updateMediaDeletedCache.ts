import type { InfiniteData, QueryClient } from "@tanstack/react-query";

import { photosQueryKey, type MediaList } from "@/entities/media";

interface Params {
  queryClient: QueryClient;
  roomId: number;
  userId: number;
  mediaIds: number[];
}

export const updateMediaDeletedCache = ({ queryClient, roomId, userId, mediaIds }: Params) => {
  const deletedIds = new Set(mediaIds);

  queryClient.setQueriesData<InfiniteData<MediaList>>(
    { queryKey: photosQueryKey(roomId, userId) },
    (current) => {
      if (!current) return current;

      const deletedCount = new Set(
        current.pages.flatMap((page) =>
          page.items.filter((item) => deletedIds.has(item.mediaId)).map((item) => item.mediaId),
        ),
      ).size;

      return {
        ...current,
        pages: current.pages.map((page) => ({
          ...page,
          items: page.items.filter((item) => !deletedIds.has(item.mediaId)),
          totalCount: Math.max(0, page.totalCount - deletedCount),
        })),
      };
    },
  );
};
