import { useQuery } from "@tanstack/react-query";

import { getMyRooms } from "../api/getMyRooms";

interface UseMyRoomsQueryParams {
  token?: string;
}

export const myRoomsQueryKey = ["myRooms"] as const;

export const useMyRoomsQuery = ({ token }: UseMyRoomsQueryParams) => {
  // 회원 한 명의 목록이라 사용자별로 캐시를 나눌 일이 없다. 토큰은 queryKey 에 노출하지 않는다.
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  return useQuery({
    queryKey: myRoomsQueryKey,
    queryFn: () => getMyRooms(token),
    retry: false,
    refetchOnMount: "always",
  });
};
