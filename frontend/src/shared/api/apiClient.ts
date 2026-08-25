import { API_BASE_URL } from "@/shared/config";
import { ApiError } from "./ApiError";
import { tokenStorage } from "./tokenStorage";

interface ApiResponse<T> {
  data: T;
}

interface ErrorBody {
  code?: string;
  message?: string;
}

/**
 * 토큰이 있으면 자동으로 붙이고, 성공 응답의 data 만 꺼내 돌려준다.
 * 실패하면 ApiError 를 던져 호출부가 상태 코드와 코드값으로 분기하게 한다.
 */
export const apiClient = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
  const token = tokenStorage.get();

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
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

  return (body as ApiResponse<T>).data;
};
