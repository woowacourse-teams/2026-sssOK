import { apiClient } from "@/shared/api";
import type { MediaLikeResponse } from "./types";

interface AddMediaLikeParams {
  roomId: number;
  mediaId: number;
  token: string;
}

export const addMediaLike = ({ roomId, mediaId, token }: AddMediaLikeParams) =>
  apiClient<MediaLikeResponse>(`/rooms/${roomId}/media/${mediaId}/likes`, {
    method: "PUT",
    token,
    errorTracking: {
      level: "error",
      operation: "gallery.add_media_like",
      route: "/rooms/:roomId/media/:mediaId/likes",
    },
  });
