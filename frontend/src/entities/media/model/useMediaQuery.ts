import { useQuery } from "@tanstack/react-query";

import { getMedia } from "../api/getMedia";

export const mediaQueryKey = (roomId: number, mediaId: number, userId: number) =>
  ["media", roomId, mediaId, userId] as const;

export const useMediaQuery = ({
  roomId,
  mediaId,
  userId,
  token,
}: {
  roomId: number;
  mediaId: number;
  userId: number;
  token: string;
}) => {
  // 사용자별 캐시로 분리하되 토큰 자체는 캐시에 기록하지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  return useQuery({
    queryKey: mediaQueryKey(roomId, mediaId, userId),
    queryFn: () => getMedia({ roomId, mediaId, token }),
    retry: false,
  });
};
