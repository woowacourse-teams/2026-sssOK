import type { UseQueryResult } from "@tanstack/react-query";
import type { Room } from "@/entities/room/model/types";
import { useQuery } from "@tanstack/react-query";

import { getRoom } from "../api/getRoom";

interface UseRoomQueryParams {
  code: string;
  token?: string;
  userId?: number;
  enabled?: boolean;
}

export const roomQueryKey = (
  code: string,
  userId?: number,
): readonly ["room", string, number | "guest"] => ["room", code, userId ?? "guest"] as const;

export const useRoomQuery = ({
  code,
  token,
  userId,
  enabled = true,
}: UseRoomQueryParams): UseQueryResult<NoInfer<Room>, Error> => {
  // userId로 사용자별 캐시를 나누므로 인증 정보인 token은 queryKey에 노출하지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps -- 인증 토큰은 캐시 식별자가 아니며 세션 변경 시 별도로 캐시를 정리한다
  return useQuery({
    queryKey: roomQueryKey(code, userId),
    queryFn: () => getRoom(code, token),
    enabled: enabled && code.length > 0,
    retry: false,
  });
};
