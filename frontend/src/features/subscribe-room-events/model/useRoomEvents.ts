import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { photosQueryKey, type Media, type MediaItem, type MediaList } from "@/entities/media";
import { API_BASE_URL } from "@/shared/config";
import { captureException } from "@/shared/lib";
import type { MediaFoldersUpdatedEvent, MediaLikesUpdatedEvent } from "./roomEventTypes";
import { updateMediaReadyCache } from "./updateMediaReadyCache";
import { updateMediaDeletedCache } from "./updateMediaDeletedCache";

interface UseRoomEventsParams {
  roomId: number;
  userId: number;
  token: string;
  onMediaCreated?: (media: Media) => void;
  onMediaDeleted?: (mediaIds: number[]) => void;
  onFoldersChanged?: () => void;
  onFolderDeleted?: (folderId: number) => void;
  onMediaFoldersUpdated?: (event: MediaFoldersUpdatedEvent) => void;
}

const FOLDER_CHANGE_EVENTS = ["folder.created", "folder.renamed", "folder.deleted"] as const;

export const useRoomEvents = ({
  roomId,
  userId,
  token,
  onMediaCreated,
  onMediaDeleted,
  onFoldersChanged,
  onFolderDeleted,
  onMediaFoldersUpdated,
}: UseRoomEventsParams) => {
  const queryClient = useQueryClient();
  const onMediaCreatedRef = useRef(onMediaCreated);
  const onMediaDeletedRef = useRef(onMediaDeleted);
  const onFoldersChangedRef = useRef(onFoldersChanged);
  const onFolderDeletedRef = useRef(onFolderDeleted);
  const onMediaFoldersUpdatedRef = useRef(onMediaFoldersUpdated);

  useEffect(() => {
    onMediaCreatedRef.current = onMediaCreated;
  }, [onMediaCreated]);

  useEffect(() => {
    onMediaDeletedRef.current = onMediaDeleted;
  }, [onMediaDeleted]);

  useEffect(() => {
    onFoldersChangedRef.current = onFoldersChanged;
  }, [onFoldersChanged]);

  useEffect(() => {
    onFolderDeletedRef.current = onFolderDeleted;
  }, [onFolderDeleted]);

  useEffect(() => {
    onMediaFoldersUpdatedRef.current = onMediaFoldersUpdated;
  }, [onMediaFoldersUpdated]);

  useEffect(() => {
    const url = new URL(`${API_BASE_URL}/rooms/${roomId}/events`);
    url.searchParams.set("token", token);

    const eventSource = new EventSource(url);
    let hasReportedConnectionError = false;

    const handleConnectionError = () => {
      if (hasReportedConnectionError) return;

      hasReportedConnectionError = true;
      captureException(new Error("실시간 구독 연결에 실패했습니다."), {
        level: "warning",
        operation: "room_events.subscribe",
        method: "GET",
        route: "/rooms/:roomId/events",
        code: "EVENT_SOURCE_ERROR",
      });
    };

    const handleMediaCreated = (event: MessageEvent<string>) => {
      const media = JSON.parse(event.data) as Media;
      onMediaCreatedRef.current?.(media);
    };

    const handleMediaReady = (event: MessageEvent<string>) => {
      const media = JSON.parse(event.data) as MediaItem;
      updateMediaReadyCache({
        queryClient,
        roomId,
        userId,
        media,
        replacedPending: false,
      });
    };

    const handleMediaDeleted = (event: MessageEvent<string>) => {
      const { mediaIds } = JSON.parse(event.data) as { mediaIds: number[] };

      updateMediaDeletedCache({ queryClient, roomId, userId, mediaIds });
      onMediaDeletedRef.current?.(mediaIds);
    };

    const handleFoldersChanged = () => {
      onFoldersChangedRef.current?.();
    };

    const handleFolderDeleted = (event: MessageEvent<string>) => {
      const { folderId } = JSON.parse(event.data) as { folderId: number };
      onFolderDeletedRef.current?.(folderId);
    };

    const handleMediaFoldersUpdated = (event: MessageEvent<string>) => {
      const payload = JSON.parse(event.data) as MediaFoldersUpdatedEvent;

      onMediaFoldersUpdatedRef.current?.(payload);
    };

    const handleMediaLikesUpdated = (event: MessageEvent<string>) => {
      const payload = JSON.parse(event.data) as MediaLikesUpdatedEvent;

      queryClient.setQueryData<MediaList>(photosQueryKey(roomId, userId), (current) =>
        current
          ? {
              ...current,
              items: current.items.map((media) =>
                media.mediaId === payload.mediaId
                  ? { ...media, likeCount: payload.likeCount }
                  : media,
              ),
            }
          : current,
      );
    };

    eventSource.addEventListener("media.created", handleMediaCreated);
    eventSource.addEventListener("media.ready", handleMediaReady);
    eventSource.addEventListener("media.deleted", handleMediaDeleted);
    FOLDER_CHANGE_EVENTS.forEach((type) =>
      eventSource.addEventListener(type, handleFoldersChanged),
    );
    eventSource.addEventListener("folder.deleted", handleFolderDeleted);
    eventSource.addEventListener("media.folders.updated", handleMediaFoldersUpdated);
    eventSource.addEventListener("media.likes.updated", handleMediaLikesUpdated);
    eventSource.addEventListener("error", handleConnectionError);

    return () => {
      eventSource.removeEventListener("media.created", handleMediaCreated);
      eventSource.removeEventListener("media.ready", handleMediaReady);
      eventSource.removeEventListener("media.deleted", handleMediaDeleted);
      FOLDER_CHANGE_EVENTS.forEach((type) =>
        eventSource.removeEventListener(type, handleFoldersChanged),
      );
      eventSource.removeEventListener("folder.deleted", handleFolderDeleted);
      eventSource.removeEventListener("media.folders.updated", handleMediaFoldersUpdated);
      eventSource.removeEventListener("media.likes.updated", handleMediaLikesUpdated);
      eventSource.removeEventListener("error", handleConnectionError);
      eventSource.close();
    };
  }, [queryClient, roomId, userId, token]);
};
