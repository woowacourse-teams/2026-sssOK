import type { MemberSession } from "../api/refreshMemberSession";
import type { UseQueryResult } from "@tanstack/react-query";
import { rememberTokenReplacement } from "@/shared/api/authRecovery";
import { notifyUnauthorized } from "@/shared/api/unauthorized";
import { queryOptions, useQuery } from "@tanstack/react-query";

import { refreshMemberSession } from "../api/refreshMemberSession";

/** 로그인 완료 시에도 이 키에 서버가 발급한 회원 세션을 저장한다. */
export const MEMBER_SESSION_QUERY_KEY = ["member-session"] as const;

/** setTimeout 이 기다릴 수 있는 가장 긴 시간(2^31 - 1ms). */
const MAX_TIMER_DELAY_MS = 2_147_483_647;

export const memberSessionQueryOptions = (): ReturnType<
  typeof queryOptions<
    MemberSession | null,
    Error,
    MemberSession | null,
    typeof MEMBER_SESSION_QUERY_KEY
  >
> =>
  queryOptions({
    queryKey: MEMBER_SESSION_QUERY_KEY,
    queryFn: async ({ client }) => {
      const previous =
        client.getQueryData<Awaited<ReturnType<typeof refreshMemberSession>>>(
          MEMBER_SESSION_QUERY_KEY,
        );
      const session = await refreshMemberSession();
      if (previous && (!session || session.userId !== previous.userId)) {
        notifyUnauthorized(previous.accessToken);
        return null;
      }
      if (previous && session) rememberTokenReplacement(previous.accessToken, session.accessToken);
      return session;
    },
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
      const untilRefresh = Date.parse(query.state.data.expiresAt) - Date.now() - 60_000;
      // 타이머는 약 24.8일보다 길게 기다리지 못한다. 넘기면 기다리지 않고 바로 실행된다.
      return Math.min(MAX_TIMER_DELAY_MS, Math.max(1000, untilRefresh));
    },
    gcTime: Infinity,
  });

export const useMemberSessionQuery = (): UseQueryResult<NoInfer<MemberSession | null>, Error> =>
  useQuery(memberSessionQueryOptions());
