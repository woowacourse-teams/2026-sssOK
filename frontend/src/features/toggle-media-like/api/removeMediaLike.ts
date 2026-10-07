import { apiClient } from "@/shared/api";

interface RemoveMediaLikeParams {
  roomId: number;
  mediaId: number;
  token: string;
}

export const removeMediaLike = ({ roomId, mediaId, token }: RemoveMediaLikeParams) =>
  apiClient<void>(`/rooms/${roomId}/media/${mediaId}/likes`, {
    method: "DELETE",
    token,
    responseType: "empty",
    errorTracking: {
      level: "error",
      operation: "gallery.remove_media_like",
      route: "/rooms/:roomId/media/:mediaId/likes",
    },
  });
