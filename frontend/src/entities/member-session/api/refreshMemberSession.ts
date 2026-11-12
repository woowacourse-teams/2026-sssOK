import { z } from "zod";
import { AnonymousSessionSchema } from "../../session/model/types";
export const MemberSessionSchema = AnonymousSessionSchema.extend(
  z.object({ memberStatus: z.literal("REGISTERED"), refreshExpiresAt: z.string() }).shape,
);
import { refreshSession } from "@/entities/session";
import { ApiError } from "@/shared/api";

export type MemberSession = z.infer<typeof MemberSessionSchema>;

/** HttpOnly 쿠키로 회원 세션을 복원한다. 토큰은 Query 캐시(메모리)에만 둔다. */
export const refreshMemberSession = async (): Promise<MemberSession | null> => {
  try {
    const session = await refreshSession();
    if (session?.memberStatus === "ANONYMOUS") return null;
    return { ...session, memberStatus: "REGISTERED" };
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null;
    throw error;
  }
};
