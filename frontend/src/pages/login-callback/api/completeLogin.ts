import type { MemberSession } from "@/entities/member-session";
import { apiClient, ApiError } from "@/shared/api";

type Provider = "kakao" | "apple";
type Callback = { provider: Provider; code: string | null; state: string | null; invalid: boolean };
let callback: Callback | null = null;
let completion: Promise<MemberSession> | null = null;

/** 분석 도구 실행 전에 인증값을 주소에서 지우고 메모리에만 보관한다. */
export const captureLoginCallback = () => {
  const match = /^\/app\/auth\/callback\/(kakao|apple)$/.exec(location.pathname);
  if (!match || !location.search) return;
  const params = new URLSearchParams(location.search);
  callback = {
    provider: match[1] as Provider,
    code: params.get("code"),
    state: params.get("state"),
    invalid:
      params.has("error") || params.getAll("code").length > 1 || params.getAll("state").length > 1,
  };
  completion = null;
  history.replaceState(history.state, "", location.pathname);
};

const complete = async (): Promise<MemberSession> => {
  const received = callback;
  callback = null;
  if (!received) throw new Error("로그인 응답이 없어요. 로그인부터 다시 시작해주세요.");

  const key = `sssok.oauth.${received.provider}`;
  const raw = sessionStorage.getItem(key);
  sessionStorage.removeItem(key);
  if (received.invalid || !received.code || received.code.length > 2048 || !raw) {
    throw new Error("로그인이 취소됐거나 요청을 확인하지 못했어요. 다시 로그인해주세요.");
  }
  let attempt: { state: string; loginVerifier: string; expiresAt: string };
  try {
    attempt = JSON.parse(raw);
  } catch {
    throw new Error("로그인 요청을 확인하지 못했어요. 다시 로그인해주세요.");
  }
  if (
    !attempt ||
    !received.state ||
    received.state !== attempt.state ||
    !/^[A-Za-z0-9_-]{43}$/.test(attempt.loginVerifier) ||
    !Number.isFinite(Date.parse(attempt.expiresAt)) ||
    Date.parse(attempt.expiresAt) <= Date.now()
  )
    throw new Error("로그인 요청이 만료됐거나 일치하지 않아요. 다시 로그인해주세요.");

  const session = await apiClient<MemberSession>(`/auth/oauth/${received.provider}/login`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json", "X-CSRF-Protection": "1" },
    body: JSON.stringify({
      code: received.code,
      state: received.state,
      loginVerifier: attempt.loginVerifier,
      refreshTokenDelivery: "COOKIE",
    }),
  });
  if (
    session?.memberStatus !== "REGISTERED" ||
    typeof session.accessToken !== "string" ||
    !session.accessToken ||
    !Number.isFinite(Date.parse(session.expiresAt)) ||
    Date.parse(session.expiresAt) <= Date.now()
  ) {
    throw new ApiError(502, "INVALID_RESPONSE", "회원 로그인 정보를 확인하지 못했어요.");
  }
  return session;
};

/** StrictMode 재실행도 같은 요청을 공유한다. 실패한 인가 코드를 자동 재전송하지 않는다. */
export const completeLogin = () => (completion ??= complete());
