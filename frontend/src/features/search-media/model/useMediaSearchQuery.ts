import { useQuery } from "@tanstack/react-query";

import { searchMedia } from "../api/searchMedia";

interface UseMediaSearchQueryParams {
  roomId: number;
  token: string;
  userId: number;
  query: string;
}

export const mediaSearchQueryKey = (roomId: number, userId: number, query: string) => [
  "mediaSearch",
  roomId,
  userId,
  query,
];

export const useMediaSearchQuery = ({ roomId, token, userId, query }: UseMediaSearchQueryParams) =>
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  useQuery({
    queryKey: mediaSearchQueryKey(roomId, userId, query),
    queryFn: () => searchMedia({ roomId, query, token }),
    enabled: query.length > 0,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
