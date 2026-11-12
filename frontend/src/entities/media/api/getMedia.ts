import { MediaDetailSchema } from "../model/types";
import { apiClient } from "@/shared/api";
import type { MediaDetail } from "../model/types";

interface GetMediaParams {
  roomId: number;
  mediaId: number;
  token: string;
}

export const getMedia = ({ roomId, mediaId, token }: GetMediaParams): Promise<MediaDetail> =>
  apiClient<MediaDetail>(`/rooms/${roomId}/media/${mediaId}`, {
    schema: MediaDetailSchema,
    token,
    errorTracking: {
      level: "error",
      operation: "gallery.get_media",
      route: "/rooms/:roomId/media/:mediaId",
    },
  });
