import { API_BASE_URL } from "@/shared/config";
import { ApiError } from "./ApiError";

/** 성공 응답은 항상 data 로 한 겹 감싸여 온다 (backend ApiResponse<T>). */
interface ApiResponse<T> {
  data: T;
}

interface ErrorBody {
  code?: string;
  message?: string;
}

export interface RequestOptions extends RequestInit {
  /**
   * 이 요청에 실을 토큰. 토큰은 방마다 다르므로 호출부가 어느 방 것인지 정해 넘긴다.
   * 없으면 Authorization 헤더를 아예 붙이지 않는다 — 빈 값을 보내면 서버가 401 로 본다.
   */
  token?: string | null;
}

/**
 * 성공 응답의 data 만 꺼내 돌려준다.
 * 실패하면 ApiError 를 던져 호출부가 상태 코드와 코드값으로 분기하게 한다.
 */
export const apiClient = async <T>(path: string, options: RequestOptions = {}): Promise<T> => {
  const { token, headers, ...init } = options;

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
  });

  // 204 나 비정상 응답이면 본문이 없을 수 있다
  const body = await response.json().catch(() => null);

  if (!response.ok) {
    const error = (body ?? {}) as ErrorBody;

    throw new ApiError(
      response.status,
      error.code ?? "UNKNOWN_ERROR",
      error.message ?? "요청을 처리하지 못했어요.",
    );
  }

  // 200 인데 본문이 없거나 JSON 이 아니면 우리 API 의 응답이 아니다.
  // (MSW 준비 전 요청이 dev 서버로 새어 index.html 을 받는 경우가 그렇다)
  if (body === null || typeof body !== "object" || !("data" in body)) {
    throw new ApiError(response.status, "INVALID_RESPONSE", "서버 응답을 이해하지 못했어요.");
  }

  return (body as ApiResponse<T>).data;
};
