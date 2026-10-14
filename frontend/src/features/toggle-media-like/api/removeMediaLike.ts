import { apiClient } from "@/shared/api";
import type { MediaLikeResponse } from "./types";

interface RemoveMediaLikeParams {
  roomId: number;
  mediaId: number;
  token: string;
}

export const removeMediaLike = ({ roomId, mediaId, token }: RemoveMediaLikeParams) =>
  apiClient<MediaLikeResponse>(`/rooms/${roomId}/media/${mediaId}/likes`, {
    method: "DELETE",
    token,
    errorTracking: {
      level: "error",
      operation: "gallery.remove_media_like",
      route: "/rooms/:roomId/media/:mediaId/likes",
    },
  });
