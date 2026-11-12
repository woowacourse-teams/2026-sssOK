import type { UseSuspenseQueryResult } from "@tanstack/react-query";
import type { MyRoom } from "@/entities/room/model/types";
import { useSuspenseQuery } from "@tanstack/react-query";

import { getMyRooms } from "../api/getMyRooms";

interface UseMyRoomsQueryParams {
  token?: string;
}

export const myRoomsQueryKey = ["myRooms"] as const;

export const useMyRoomsQuery = ({
  token,
}: UseMyRoomsQueryParams): UseSuspenseQueryResult<MyRoom[], Error> => {
  // 회원 한 명의 목록이라 사용자별로 캐시를 나눌 일이 없다. 토큰은 queryKey 에 노출하지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps -- 인증 토큰은 캐시 식별자가 아니며 세션 변경 시 별도로 캐시를 정리한다
  return useSuspenseQuery({
    queryKey: myRoomsQueryKey,
    queryFn: () => getMyRooms(token),
    retry: false,
    refetchOnMount: "always",
  });
};
