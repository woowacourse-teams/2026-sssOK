import { z } from "zod";
import { AnonymousSessionSchema } from "../model/types";
export const RefreshedSessionSchema = AnonymousSessionSchema.extend(
  z.object({
    memberStatus: z.union([z.literal("REGISTERED"), z.literal("ANONYMOUS")]),
    refreshExpiresAt: z.string(),
  }).shape,
);
import { rememberTokenReplacement } from "@/shared/api/authRecovery";
import { apiClient, ApiError } from "@/shared/api";
import { getRoomSession, saveRoomSession } from "../lib/roomSessionStorage";

export type RefreshedSession = z.infer<typeof RefreshedSessionSchema>;

let pending: Promise<RefreshedSession> | null = null;

/** 초기 복원, 선제 갱신, 401 복구가 일회용 refresh 쿠키를 함께 사용한다. */
export const refreshSession = (): Promise<RefreshedSession> => {
  if (pending) return pending;
  const run = async (): Promise<RefreshedSession> => {
    const rooms = Object.keys(localStorage)
      .filter((key) => key.startsWith("sssok.auth:"))
      .map((key) => {
        const code = key.slice("sssok.auth:".length);
        return { code, token: getRoomSession(code)?.accessToken };
      });
    const session = await apiClient<RefreshedSession>("/auth/refresh", {
      schema: RefreshedSessionSchema,
      method: "POST",
      credentials: "include",
      headers: { "X-CSRF-Protection": "1" },
    });
    if (
      !session ||
      !["REGISTERED", "ANONYMOUS"].includes(session.memberStatus) ||
      typeof session.accessToken !== "string" ||
      !session.accessToken ||
      !Number.isSafeInteger(session.userId) ||
      !Number.isFinite(Date.parse(session.expiresAt)) ||
      Date.parse(session.expiresAt) <= Date.now()
    )
      throw new ApiError(502, "INVALID_RESPONSE", "로그인 정보를 확인하지 못했어요.");

    // 회원 확인 과정에서도 익명 refresh 쿠키가 회전할 수 있다. 같은 주체의 방만 갱신한다.
    if (session.memberStatus === "ANONYMOUS") {
      for (const { code, token } of rooms) {
        const current = getRoomSession(code);
        if (token && current?.accessToken === token && current.userId === session.userId) {
          saveRoomSession(code, {
            accessToken: session.accessToken,
            userId: session.userId,
            nickname: session.nickname,
            expiresAt: session.expiresAt,
            refreshExpiresAt: session.refreshExpiresAt,
          });
          rememberTokenReplacement(token, session.accessToken);
        }
      }
    }
    return session;
  };
  // 지원하는 브라우저에서는 탭 사이의 일회용 쿠키 사용도 직렬화한다.
  pending = (navigator.locks ? navigator.locks.request("sssok-auth-refresh", run) : run()).finally(
    () => {
      pending = null;
    },
  );
  return pending;
};
