import type { MediaList } from "@/entities/media";
import { apiClient } from "@/shared/api";

interface SearchMediaParams {
  roomId: number;
  query: string;
  token: string;
}

// 전체 목록 API 에 query 를 붙이면 검색 결과를 유사도 높은 순으로 내려준다.
// 분석이 끝난 사진만 대상이라 영상과 방금 올린 사진은 결과에 없다.
export const searchMedia = ({ roomId, query, token }: SearchMediaParams) =>
  apiClient<MediaList>(`/rooms/${roomId}/media/all?${new URLSearchParams({ query }).toString()}`, {
    token,
    errorTracking: {
      // 검색 기능이 꺼져 있는 동안에는 검색할 때마다 503 이 오므로 오류로 쌓지 않는다.
      level: "warning",
      operation: "gallery.search_media",
      route: "/rooms/:roomId/media/all",
    },
  });
