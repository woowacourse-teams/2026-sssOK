import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { MEMBER_SESSION_QUERY_KEY, type MemberSession } from "@/entities/member-session";
import {
  findRoomCodeByToken,
  getRoomSession,
  refreshSession,
  removeRoomSession,
} from "@/entities/session";
import { ApiError, setUnauthorizedHandler } from "@/shared/api";
import {
  clearTokenReplacements,
  getTokenReplacement,
  rememberTokenReplacement,
  setAuthRecovery,
} from "@/shared/api/authRecovery";
import { ROUTES } from "@/shared/config";

/** 공통 인증 복구에 세션 저장소와 플랫폼별 화면 이동을 연결한다. */
export const useUnauthorizedRecovery = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useEffect(() => {
    let active = true;
    const current = (token: string) => {
      const member = queryClient.getQueryData<MemberSession | null>(MEMBER_SESSION_QUERY_KEY);
      if (member?.accessToken === token) return { session: member, roomCode: null };
      const roomCode = findRoomCodeByToken(token);
      const session = roomCode === null ? null : getRoomSession(roomCode);
      return session ? { session, roomCode } : null;
    };
    const fail = (token: string) => {
      if (!active) return;
      const context = current(token);
      if (!context) return;
      if (context.roomCode !== null) removeRoomSession(context.roomCode);
      navigate(window.ReactNativeWebView ? ROUTES.login : ROUTES.home, {
        replace: true,
        flushSync: true,
      });
      // 진행 중인 요청도 취소해 이전 계정의 데이터가 다시 캐시에 들어오지 않게 한다.
      void queryClient.cancelQueries();
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== "member-session" });
      queryClient.setQueryData(MEMBER_SESSION_QUERY_KEY, null);
      clearTokenReplacements();
    };
    const disposeFailure = setUnauthorizedHandler(fail);
    const disposeRecovery = setAuthRecovery(async (token) => {
      const replacement = getTokenReplacement(token);
      if (replacement && current(replacement)) return replacement;
      const context = current(token);
      if (!context) return null;
      try {
        const session = await refreshSession();
        if (!active) return null;
        const updated = getTokenReplacement(token);
        if (updated && current(updated)) return updated;
        // 익명 저장소는 refreshSession에서 갱신된다. 로그아웃/전환과 구분한다.
        const stillCurrent =
          context.roomCode === null
            ? current(token) !== null
            : getRoomSession(context.roomCode)?.accessToken === session.accessToken;
        if (!stillCurrent) return null;
        if (
          session.userId !== context.session.userId ||
          (context.roomCode === null && session.memberStatus !== "REGISTERED") ||
          (context.roomCode !== null && session.memberStatus !== "ANONYMOUS")
        ) {
          fail(token);
          return null;
        }
        if (context.roomCode === null) queryClient.setQueryData(MEMBER_SESSION_QUERY_KEY, session);
        rememberTokenReplacement(token, session.accessToken);
        return session.accessToken;
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          fail(token);
          return null;
        }
        throw error;
      }
    });
    return () => {
      active = false;
      disposeFailure();
      disposeRecovery();
    };
  }, [navigate, queryClient]);
};
