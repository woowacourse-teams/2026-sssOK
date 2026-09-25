import { apiClient } from "@/shared/api";
import type { MediaList } from "../model/types";

interface GetPhotosParams {
  roomId: number;
  token: string;
}

// 방의 미디어를 페이지 없이 전부 받는다.

export const getPhotos = ({ roomId, token }: GetPhotosParams) =>
  apiClient<MediaList>(`/rooms/${roomId}/media/all`, { token });
