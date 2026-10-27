import { useQuery } from "@tanstack/react-query";

import { refreshMemberSession } from "../api/refreshMemberSession";

/** 로그인 완료 시에도 이 키에 서버가 발급한 회원 세션을 저장한다. */
export const MEMBER_SESSION_QUERY_KEY = ["member-session"] as const;

export const useMemberSessionQuery = () =>
  useQuery({
    queryKey: MEMBER_SESSION_QUERY_KEY,
    queryFn: refreshMemberSession,
    // 일회용 refresh 토큰을 자동 재시도하지 않는다. 동일 Query의 동시 호출은 합쳐진다.
    retry: false,
    staleTime: (query) => {
      const session = query.state.data;
      if (session === null) return Infinity;
      return session
        ? Math.max(0, Date.parse(session.expiresAt) - query.state.dataUpdatedAt - 60_000)
        : 0;
    },
    refetchInterval: (query) => {
      if (query.state.status === "error" || !query.state.data) return false;
      return Math.max(1000, Date.parse(query.state.data.expiresAt) - Date.now() - 60_000);
    },
    gcTime: Infinity,
  });
