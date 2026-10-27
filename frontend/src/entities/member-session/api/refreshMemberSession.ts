import type { AnonymousSession } from "@/entities/session";
import { apiClient, ApiError } from "@/shared/api";

export interface MemberSession extends AnonymousSession {
  memberStatus: "REGISTERED";
  refreshExpiresAt: string;
}

/** HttpOnly 쿠키로 회원 세션을 복원한다. 토큰은 Query 캐시(메모리)에만 둔다. */
export const refreshMemberSession = async (): Promise<MemberSession | null> => {
  try {
    const session = await apiClient<
      MemberSession | (AnonymousSession & { memberStatus: "ANONYMOUS" })
    >("/auth/refresh", {
      method: "POST",
      credentials: "include",
      headers: { "X-CSRF-Protection": "1" },
    });
    if (session?.memberStatus === "ANONYMOUS") return null;
    if (
      session?.memberStatus !== "REGISTERED" ||
      typeof session.accessToken !== "string" ||
      !session.accessToken ||
      !Number.isFinite(Date.parse(session.expiresAt)) ||
      Date.parse(session.expiresAt) <= Date.now()
    ) {
      throw new ApiError(502, "INVALID_RESPONSE", "로그인 정보를 확인하지 못했어요.");
    }
    return session;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
};
