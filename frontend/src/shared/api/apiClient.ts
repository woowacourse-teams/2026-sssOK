import { ApiError } from "./ApiError";

interface ApiClientOptions extends RequestInit {
  token?: string;
}

interface ErrorResponse {
  code?: string;
  message?: string;
}

const getErrorResponse = async (response: Response): Promise<ErrorResponse> => {
  try {
    return (await response.json()) as ErrorResponse;
  } catch {
    return {};
  }
};

export const apiClient = async <T>(
  url: string,
  { token, headers, ...options }: ApiClientOptions = {},
): Promise<T> => {
  const requestHeaders = new Headers(headers);

  if (token) {
    requestHeaders.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;

  try {
    response = await fetch(url, {
      ...options,
      headers: requestHeaders,
    });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "네트워크 연결을 확인해주세요.");
  }

  if (!response.ok) {
    const error = await getErrorResponse(response);

    throw new ApiError(
      response.status,
      error.code ?? "UNKNOWN_ERROR",
      error.message ?? "API 요청에 실패했습니다.",
    );
  }

  return response.json() as Promise<T>;
};
