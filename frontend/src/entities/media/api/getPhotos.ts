import { apiClient } from "@/shared/api";
import type { MediaList } from "../model/types";

interface GetPhotosParams {
  roomId: number;
  token: string;
}

export const getPhotos = ({ roomId, token }: GetPhotosParams) =>
  apiClient<MediaList>(`/rooms/${roomId}/media`, { token });
