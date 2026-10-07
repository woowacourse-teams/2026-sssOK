import { apiClient } from "@/shared/api";

interface AddMediaLikeParams {
  roomId: number;
  mediaId: number;
  token: string;
}

export const addMediaLike = ({ roomId, mediaId, token }: AddMediaLikeParams) =>
  apiClient<void>(`/rooms/${roomId}/media/${mediaId}/likes`, {
    method: "PUT",
    token,
    responseType: "empty",
    errorTracking: {
      level: "error",
      operation: "gallery.add_media_like",
      route: "/rooms/:roomId/media/:mediaId/likes",
    },
  });
