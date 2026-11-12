import { z } from "zod";
import { MemberSessionSchema } from "../../../entities/member-session/api/refreshMemberSession";
export const AppleLoginAttemptSchema = z.object({
  state: z.string(),
  loginVerifier: z.string(),
  nonce: z.string(),
  expiresAt: z.string(),
});
import type { MemberSession } from "@/entities/member-session";
import { apiClient, ApiError } from "@/shared/api";

export type AppleLoginAttempt = z.infer<typeof AppleLoginAttemptSchema>;

export const startAppleNativeLogin = async (signal: AbortSignal): Promise<AppleLoginAttempt> => {
  const attempt = await apiClient<AppleLoginAttempt>("/auth/oauth/apple/native/start", {
    schema: AppleLoginAttemptSchema,
    method: "POST",
    credentials: "omit",
    signal,
  });
  const confirmation = /^[A-Za-z0-9_-]{43}$/;
  if (
    !attempt ||
    ![attempt.state, attempt.loginVerifier, attempt.nonce].every(
      (value) => typeof value === "string" && confirmation.test(value),
    ) ||
    !Number.isFinite(Date.parse(attempt.expiresAt)) ||
    Date.parse(attempt.expiresAt) <= Date.now()
  ) {
    throw new ApiError(502, "INVALID_RESPONSE", "Apple 로그인 요청을 확인하지 못했어요.");
  }
  return attempt;
};

/** 웹뷰에서 요청해 refresh 쿠키를 받고, 본문의 회원 세션을 검증한다. */
const requestSession = async (
  path: string,
  body: Record<string, string>,
  signal: AbortSignal,
): Promise<MemberSession> => {
  const session = await apiClient<MemberSession>(path, {
    schema: MemberSessionSchema,
    method: "POST",
    credentials: "include",
    signal,
    headers: { "Content-Type": "application/json", "X-CSRF-Protection": "1" },
    body: JSON.stringify({ ...body, refreshTokenDelivery: "COOKIE" }),
  });
  if (
    session?.memberStatus !== "REGISTERED" ||
    typeof session.accessToken !== "string" ||
    !session.accessToken ||
    !Number.isFinite(session.userId) ||
    !Number.isFinite(Date.parse(session.expiresAt)) ||
    Date.parse(session.expiresAt) <= Date.now()
  ) {
    throw new ApiError(502, "INVALID_RESPONSE", "회원 로그인 정보를 확인하지 못했어요.");
  }
  return session;
};

export const completeKakaoNativeLogin = (
  kakaoAccessToken: string,
  signal: AbortSignal,
): Promise<MemberSession> =>
  requestSession("/auth/oauth/kakao/native/login", { kakaoAccessToken }, signal);

export const completeAppleNativeLogin = (
  code: string,
  attempt: AppleLoginAttempt,
  signal: AbortSignal,
): Promise<MemberSession> =>
  requestSession(
    "/auth/oauth/apple/native/login",
    {
      code,
      state: attempt.state,
      loginVerifier: attempt.loginVerifier,
    },
    signal,
  );
