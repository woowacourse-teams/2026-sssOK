import { useEffect } from "react";
import type { QueryClient } from "@tanstack/react-query";

import { MEMBER_SESSION_QUERY_KEY, type MemberSession } from "@/entities/member-session";
import { markInternalMember } from "@/shared/lib";

/**
 * 회원 세션이 캐시에 들어오면 팀원 계정인지 보고, 팀원이면 이 기기를 팀원 기기로 표시한다.
 *
 * 세션을 새로 부르지 않는다. 로그인·방 목록처럼 이미 세션을 받는 화면이 캐시에 넣은 값만 본다 —
 * 게스트만 쓰는 화면에서 세션 갱신 요청을 하나 더 만들지 않기 위해서다.
 */
export const useInternalMemberFlag = (queryClient: QueryClient): void => {
  useEffect(() => {
    const check = (): void => {
      const session = queryClient.getQueryData<MemberSession | null>(MEMBER_SESSION_QUERY_KEY);
      if (session) markInternalMember(session.userId);
    };

    check();
    return queryClient.getQueryCache().subscribe((event) => {
      if (event.type !== "updated") return;
      if (event.query.queryKey[0] !== MEMBER_SESSION_QUERY_KEY[0]) return;
      check();
    });
  }, [queryClient]);
};
