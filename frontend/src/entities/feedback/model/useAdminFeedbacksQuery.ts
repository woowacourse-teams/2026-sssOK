import { useInfiniteQuery } from "@tanstack/react-query";

import { getAdminFeedbacks } from "../api/getAdminFeedbacks";

export const adminFeedbacksQueryKey = ["admin", "feedbacks"] as const;

/**
 * 의견 목록을 커서로 이어 불러온다.
 *
 * 인증 정보인 token 은 queryKey 에 넣지 않는다 — 관리자 계정은 앱에 하나뿐이라
 * 캐시를 나눌 이유가 없고, 키에 넣으면 토큰이 devtools 에 그대로 드러난다.
 */
export const useAdminFeedbacksQuery = (token: string) =>
  // eslint-disable-next-line @tanstack/query/exhaustive-deps
  useInfiniteQuery({
    queryKey: adminFeedbacksQueryKey,
    queryFn: ({ pageParam }) => getAdminFeedbacks({ token, cursor: pageParam }),
    // 첫 페이지는 커서 없이 부른다 — 서버가 최신부터 내려준다.
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) => {
      // hasNext 와 nextCursor 를 둘 다 본다. 서버가 hasNext 만 true 로 주고 커서를
      // 빠뜨리면 같은 페이지를 무한히 다시 부르게 된다.
      if (!lastPage.hasNext || lastPage.nextCursor === null) return undefined;

      return lastPage.nextCursor;
    },
  });
