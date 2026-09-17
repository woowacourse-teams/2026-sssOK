import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { photosQueryKey } from "@/entities/media";
import { API_BASE_URL } from "@/shared/config";

interface UseRoomEventsParams {
  roomId: number;
  userId: number;
  token: string;
}

export const useRoomEvents = ({ roomId, userId, token }: UseRoomEventsParams) => {
  const queryClient = useQueryClient();

  useEffect(() => {
    const url = new URL(`${API_BASE_URL}/rooms/${roomId}/events`);
    url.searchParams.set("token", token);

    const eventSource = new EventSource(url);

    const refreshPhotos = () => {
      void queryClient.invalidateQueries({
        queryKey: photosQueryKey(roomId, userId),
      });
    };

    eventSource.addEventListener("media.ready", refreshPhotos);

    return () => {
      eventSource.removeEventListener("media.ready", refreshPhotos);
      eventSource.close();
    };
  }, [queryClient, roomId, userId, token]);
};
