import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { photosQueryKey, type MediaItem, type MediaList } from "@/entities/media";
import { API_BASE_URL } from "@/shared/config";

interface UseRoomEventsParams {
  roomId: number;
  userId: number;
  token: string;
  onMediaReady?: (media: MediaItem) => boolean;
  onMediaDeleted?: (mediaIds: number[]) => void;
}

export const useRoomEvents = ({
  roomId,
  userId,
  token,
  onMediaReady,
  onMediaDeleted,
}: UseRoomEventsParams) => {
  const queryClient = useQueryClient();
  const onMediaReadyRef = useRef(onMediaReady);
  const onMediaDeletedRef = useRef(onMediaDeleted);

  useEffect(() => {
    onMediaReadyRef.current = onMediaReady;
  }, [onMediaReady]);

  useEffect(() => {
    onMediaDeletedRef.current = onMediaDeleted;
  }, [onMediaDeleted]);

  useEffect(() => {
    const url = new URL(`${API_BASE_URL}/rooms/${roomId}/events`);
    url.searchParams.set("token", token);

    const eventSource = new EventSource(url);

    const handleMediaReady = (event: MessageEvent<string>) => {
      const media = JSON.parse(event.data) as MediaItem;
      const replacedPending =
        media.uploaderId === userId && (onMediaReadyRef.current?.(media) ?? false);

      queryClient.setQueryData<MediaList>(photosQueryKey(roomId, userId), (current) => {
        if (!current) return current;

        const exists = current.items.some((item) => item.mediaId === media.mediaId);
        if (exists) {
          return {
            ...current,
            items: current.items.map((item) => (item.mediaId === media.mediaId ? media : item)),
          };
        }
        // 내가 올린 것은 미리보기 자리가 이미 있다. 목록에 또 넣으면 두 장으로 보인다.
        if (replacedPending) return current;

        return { ...current, items: [media, ...current.items] };
      });
    };

    const handleMediaDeleted = (event: MessageEvent<string>) => {
      const { mediaIds } = JSON.parse(event.data) as { mediaIds: number[] };
      const deletedIds = new Set(mediaIds);

      queryClient.setQueryData<MediaList>(photosQueryKey(roomId, userId), (current) =>
        current
          ? { ...current, items: current.items.filter((item) => !deletedIds.has(item.mediaId)) }
          : current,
      );
      onMediaDeletedRef.current?.(mediaIds);
    };

    eventSource.addEventListener("media.ready", handleMediaReady);
    eventSource.addEventListener("media.deleted", handleMediaDeleted);

    return () => {
      eventSource.removeEventListener("media.ready", handleMediaReady);
      eventSource.removeEventListener("media.deleted", handleMediaDeleted);
      eventSource.close();
    };
  }, [queryClient, roomId, userId, token]);
};
