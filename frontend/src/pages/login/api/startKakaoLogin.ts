import { apiClient, ApiError } from "@/shared/api";

interface KakaoLoginStart {
  authorizationUrl: string;
  state: string;
  loginVerifier: string;
  expiresAt: string;
}

/** 콜백에서 사용할 확인값을 같은 탭에 보관하고 카카오 인증 주소를 반환한다. */
export const startKakaoLogin = async (): Promise<string> => {
  sessionStorage.removeItem("sssok.oauth.kakao");
  const result = await apiClient<KakaoLoginStart>("/auth/oauth/kakao/start", {
    method: "POST",
    credentials: "omit",
  });
  let url: URL;
  try {
    url = new URL(result.authorizationUrl);
  } catch {
    throw new ApiError(502, "INVALID_RESPONSE", "카카오 로그인 주소를 확인하지 못했어요.");
  }
  const confirmation = /^[A-Za-z0-9_-]{43}$/;
  if (
    url.origin !== "https://kauth.kakao.com" ||
    url.pathname !== "/oauth/authorize" ||
    !confirmation.test(result.state) ||
    !confirmation.test(result.loginVerifier) ||
    url.searchParams.get("state") !== result.state ||
    !Number.isFinite(Date.parse(result.expiresAt)) ||
    Date.parse(result.expiresAt) <= Date.now()
  ) {
    throw new ApiError(502, "INVALID_RESPONSE", "카카오 로그인 요청을 확인하지 못했어요.");
  }
  sessionStorage.setItem(
    "sssok.oauth.kakao",
    JSON.stringify({
      state: result.state,
      loginVerifier: result.loginVerifier,
      expiresAt: result.expiresAt,
    }),
  );
  return url.href;
};
