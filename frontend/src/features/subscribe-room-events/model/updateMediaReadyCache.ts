import type { InfiniteData, QueryClient } from "@tanstack/react-query";

import {
  photosQueryKey,
  type MediaItem,
  type MediaList,
  type MediaUploaderFilter,
} from "@/entities/media";

interface Params {
  queryClient: QueryClient;
  roomId: number;
  userId: number;
  media: MediaItem;
  replacedPending: boolean;
}

export const updateMediaReadyCache = ({
  queryClient,
  roomId,
  userId,
  media,
  replacedPending,
}: Params) => {
  const queries = queryClient.getQueryCache().findAll({ queryKey: photosQueryKey(roomId, userId) });

  queries.forEach((query) => {
    const filter = query.queryKey[3] as
      { folderId: number | null; uploader: MediaUploaderFilter } | undefined;
    const matchesFolder =
      filter?.folderId === null ||
      filter?.folderId === undefined ||
      media.folderIds.includes(filter.folderId);
    const matchesUploader =
      filter?.uploader === undefined ||
      filter.uploader === "ALL" ||
      (filter.uploader === "ME" && media.uploaderId === userId) ||
      (filter.uploader === "OTHERS" && media.uploaderId !== userId);
    if (!matchesFolder || !matchesUploader) return;

    queryClient.setQueryData<InfiniteData<MediaList>>(query.queryKey, (current) => {
      if (!current) return current;

      const exists = current.pages.some((page) =>
        page.items.some((item) => item.mediaId === media.mediaId),
      );
      const pages = current.pages.map((page) => ({
        ...page,
        items: page.items.map((item) => (item.mediaId === media.mediaId ? media : item)),
        totalCount: exists ? page.totalCount : page.totalCount + 1,
      }));
      if (!exists && !replacedPending && pages[0]) {
        pages[0] = { ...pages[0], items: [media, ...pages[0].items] };
      }

      return { ...current, pages };
    });
  });
};
