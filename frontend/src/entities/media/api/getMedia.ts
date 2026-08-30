import { apiClient } from "@/shared/api";
import type { MediaDetail } from "../model/types";

interface GetMediaParams {
  roomId: number;
  mediaId: number;
  token: string;
}

export const getMedia = ({ roomId, mediaId, token }: GetMediaParams) =>
  apiClient<MediaDetail>(`/rooms/${roomId}/media/${mediaId}`, { token });
