import { useQuery } from "@tanstack/react-query";

import { getRoom } from "./getRoom";

export const roomQueryKey = (code: string) => ["room", code] as const;

export const useRoom = (code: string) =>
  useQuery({
    queryKey: roomQueryKey(code),
    queryFn: () => getRoom(code),
    // 없는 방·잘못된 코드는 다시 물어도 답이 같다
    retry: false,
  });
