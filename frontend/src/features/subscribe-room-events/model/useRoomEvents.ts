import { useEffect, useRef } from "react";
import { type InfiniteData, useQueryClient } from "@tanstack/react-query";

import {
  photosQueryKey,
  type MediaItem,
  type MediaList,
  type MediaUploaderFilter,
} from "@/entities/media";
import { API_BASE_URL } from "@/shared/config";

interface UseRoomEventsParams {
  roomId: number;
  userId: number;
  token: string;
  onMediaReady?: (media: MediaItem) => void;
}

export const useRoomEvents = ({ roomId, userId, token, onMediaReady }: UseRoomEventsParams) => {
  const queryClient = useQueryClient();
  const onMediaReadyRef = useRef(onMediaReady);

  useEffect(() => {
    onMediaReadyRef.current = onMediaReady;
  }, [onMediaReady]);

  useEffect(() => {
    const url = new URL(`${API_BASE_URL}/rooms/${roomId}/events`);
    url.searchParams.set("token", token);

    const eventSource = new EventSource(url);

    const handleMediaReady = (event: MessageEvent<string>) => {
      const media = JSON.parse(event.data) as MediaItem;

      const queries = queryClient
        .getQueryCache()
        .findAll({ queryKey: photosQueryKey(roomId, userId) });

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
          if (!exists && pages[0]) pages[0] = { ...pages[0], items: [media, ...pages[0].items] };

          return { ...current, pages };
        });
      });
      onMediaReadyRef.current?.(media);
    };

    eventSource.addEventListener("media.ready", handleMediaReady);

    return () => {
      eventSource.removeEventListener("media.ready", handleMediaReady);
      eventSource.close();
    };
  }, [queryClient, roomId, userId, token]);
};
