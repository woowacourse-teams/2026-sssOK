import type { MediaItem } from "@/entities/media";
import { apiClient } from "@/shared/api";

export interface MediaSearchMatch {
  media: MediaItem;
  similarity: number;
}

interface SearchMediaParams {
  roomId: number;
  query: string;
  token: string;
}

// 분석이 끝난 사진만 대상이라 영상과 방금 올린 사진은 결과에 없다.
export const searchMedia = ({ roomId, query, token }: SearchMediaParams) =>
  apiClient<MediaSearchMatch[]>(
    `/rooms/${roomId}/media/search?${new URLSearchParams({ query }).toString()}`,
    {
      token,
      errorTracking: {
        // 검색 기능이 꺼져 있는 동안에는 검색할 때마다 503 이 오므로 오류로 쌓지 않는다.
        level: "warning",
        operation: "gallery.search_media",
        route: "/rooms/:roomId/media/search",
      },
    },
  );
