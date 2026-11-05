import { readAnonymousAccessTokens } from "@/entities/session";
import { apiClient, isApiError } from "@/shared/api";

const post = <T>(path: string, tokens: string[]) =>
  apiClient<T>(path, {
    method: "POST",
    credentials: "omit",
    ...(tokens.length > 0 && {
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ anonymousAccessTokens: tokens }),
    }),
  });

/**
 * 소셜 로그인 시작 API를 부른다. 이 기기의 익명 토큰을 함께 보내 익명 활동을 회원 계정에 합친다.
 *
 * 서버는 토큰 하나라도 받아들이지 못하면 시작 자체를 거절한다. 그 토큰 때문에 로그인을 못 하면
 * 안 되므로, 요청이 거절(4xx)되면 토큰 없이 한 번 더 시작한다. 그때는 익명 활동이 합쳐지지 않는다.
 */
export const requestLoginStart = async <T>(path: string): Promise<T> => {
  const tokens = readAnonymousAccessTokens();
  try {
    return await post<T>(path, tokens);
  } catch (error) {
    const rejected = isApiError(error) && error.status >= 400 && error.status < 500;
    if (tokens.length === 0 || !rejected) throw error;
    return post<T>(path, []);
  }
};
