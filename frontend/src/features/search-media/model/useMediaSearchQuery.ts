import type { UseQueryResult } from "@tanstack/react-query";
import type { MediaList } from "@/entities/media/model/types";
import { useQuery } from "@tanstack/react-query";

import { searchMedia } from "../api/searchMedia";

interface UseMediaSearchQueryParams {
  roomId: number;
  token: string;
  userId: number;
  query: string;
}

export const mediaSearchQueryKey = (
  roomId: number,
  userId: number,
  query: string,
): (string | number)[] => ["mediaSearch", roomId, userId, query];

export const useMediaSearchQuery = ({
  roomId,
  token,
  userId,
  query,
}: UseMediaSearchQueryParams): UseQueryResult<NoInfer<MediaList>, Error> =>
  // eslint-disable-next-line @tanstack/query/exhaustive-deps -- 인증 토큰은 캐시 식별자가 아니며 세션 변경 시 별도로 캐시를 정리한다
  useQuery({
    queryKey: mediaSearchQueryKey(roomId, userId, query),
    queryFn: () => searchMedia({ roomId, query, token }),
    enabled: query.length > 0,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
