import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";

import type { MediaItem } from "@/entities/media";
import { API_BASE_URL } from "@/shared/config";
import type { MediaFoldersUpdatedEvent } from "./roomEventTypes";
import { updateMediaReadyCache } from "./updateMediaReadyCache";
import { updateMediaDeletedCache } from "./updateMediaDeletedCache";

interface UseRoomEventsParams {
  roomId: number;
  userId: number;
  token: string;
  onMediaReady?: (media: MediaItem) => boolean;
  onMediaDeleted?: (mediaIds: number[]) => void;
  onMediaFoldersUpdated?: (event: MediaFoldersUpdatedEvent) => void;
}

export const useRoomEvents = ({
  roomId,
  userId,
  token,
  onMediaReady,
  onMediaDeleted,
  onMediaFoldersUpdated,
}: UseRoomEventsParams) => {
  const queryClient = useQueryClient();
  const onMediaReadyRef = useRef(onMediaReady);
  const onMediaDeletedRef = useRef(onMediaDeleted);
  const onMediaFoldersUpdatedRef = useRef(onMediaFoldersUpdated);

  useEffect(() => {
    onMediaReadyRef.current = onMediaReady;
  }, [onMediaReady]);

  useEffect(() => {
    onMediaDeletedRef.current = onMediaDeleted;
  }, [onMediaDeleted]);

  useEffect(() => {
    onMediaFoldersUpdatedRef.current = onMediaFoldersUpdated;
  }, [onMediaFoldersUpdated]);

  useEffect(() => {
    const url = new URL(`${API_BASE_URL}/rooms/${roomId}/events`);
    url.searchParams.set("token", token);

    const eventSource = new EventSource(url);

    const handleMediaReady = (event: MessageEvent<string>) => {
      const media = JSON.parse(event.data) as MediaItem;
      const replacedPending =
        media.uploaderId === userId && (onMediaReadyRef.current?.(media) ?? false);

      updateMediaReadyCache({
        queryClient,
        roomId,
        userId,
        media,
        replacedPending,
      });
    };

    const handleMediaDeleted = (event: MessageEvent<string>) => {
      const { mediaIds } = JSON.parse(event.data) as { mediaIds: number[] };

      updateMediaDeletedCache({ queryClient, roomId, userId, mediaIds });
      onMediaDeletedRef.current?.(mediaIds);
    };

    const handleMediaFoldersUpdated = (event: MessageEvent<string>) => {
      const payload = JSON.parse(event.data) as MediaFoldersUpdatedEvent;

      onMediaFoldersUpdatedRef.current?.(payload);
    };

    eventSource.addEventListener("media.ready", handleMediaReady);
    eventSource.addEventListener("media.deleted", handleMediaDeleted);
    eventSource.addEventListener("media.folders.updated", handleMediaFoldersUpdated);

    return () => {
      eventSource.removeEventListener("media.ready", handleMediaReady);
      eventSource.removeEventListener("media.deleted", handleMediaDeleted);
      eventSource.removeEventListener("media.folders.updated", handleMediaFoldersUpdated);
      eventSource.close();
    };
  }, [queryClient, roomId, userId, token]);
};
