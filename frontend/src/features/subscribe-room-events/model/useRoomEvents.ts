import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { photosQueryKey } from "@/entities/media";
import { API_BASE_URL } from "@/shared/config";

interface UseRoomEventsParams {
  roomId: number;
  userId: number;
  token: string;
}

const ROOM_EVENT_NAMES = [
  "media.created",
  "media.ready",
  "media.failed",
  "media.deleted",
  "media.folders.updated",
  "folder.created",
  "folder.updated",
  "folder.deleted",
  "room.updated",
  "room.deleted",
] as const;

const parseEventData = (data: string) => {
  try {
    return JSON.parse(data) as unknown;
  } catch {
    return data;
  }
};

/** 방에 접속해 있는 동안 media.ready 이벤트를 받아 사진 목록을 갱신한다. */
export const useRoomEvents = ({ roomId, userId, token }: UseRoomEventsParams) => {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (typeof EventSource === "undefined") return;

    const url = new URL(`${API_BASE_URL}/rooms/${roomId}/events`, window.location.origin);
    url.searchParams.set("token", token);

    const eventSource = new EventSource(url);
    const refreshPhotos = () => {
      void queryClient.invalidateQueries({ queryKey: photosQueryKey(roomId, userId) });
    };
    const logConnected = () => {
      console.info(`[SSE] ${roomId}번 방 구독 연결됨`);
    };
    const logEvent = (event: Event) => {
      const message = event as MessageEvent<string>;
      console.info(`[SSE] ${message.type}`, {
        eventId: message.lastEventId,
        data: parseEventData(message.data),
      });
    };
    const logError = () => {
      console.warn(`[SSE] ${roomId}번 방 연결 오류 또는 재연결 대기`, {
        readyState: eventSource.readyState,
      });
    };

    eventSource.addEventListener("media.ready", refreshPhotos);
    if (process.env.NODE_ENV === "development") {
      eventSource.addEventListener("open", logConnected);
      eventSource.addEventListener("error", logError);
      ROOM_EVENT_NAMES.forEach((eventName) => eventSource.addEventListener(eventName, logEvent));
    }

    return () => {
      eventSource.removeEventListener("media.ready", refreshPhotos);
      if (process.env.NODE_ENV === "development") {
        eventSource.removeEventListener("open", logConnected);
        eventSource.removeEventListener("error", logError);
        ROOM_EVENT_NAMES.forEach((eventName) =>
          eventSource.removeEventListener(eventName, logEvent),
        );
      }
      eventSource.close();
    };
  }, [queryClient, roomId, token, userId]);
};
